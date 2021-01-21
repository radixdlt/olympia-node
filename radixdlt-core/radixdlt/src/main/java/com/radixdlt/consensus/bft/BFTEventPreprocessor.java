/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.bft;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTSyncer.SyncResult;

import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Preprocesses consensus events and ensures that the vertexStore is synced to
 * the correct state before they get forwarded to the actual state reducer.
 *
 * This class should not be updating any part of the BFT Safety state besides
 * the VertexStore.
 *
 * A lot of the queue logic could be done more "cleanly" and functionally using
 * lambdas and Functions but the performance impact is too great.
 *
 * This class is NOT thread-safe.
 */
public final class BFTEventPreprocessor implements BFTEventProcessor {
	private static final Logger log = LogManager.getLogger();

	private final BFTEventProcessor forwardTo;
	private final BFTSyncer bftSyncer;
	private final Set<ConsensusEvent> syncingEvents = new HashSet<>();
	private final Map<View, List<ConsensusEvent>> viewQueues = new HashMap<>();
	private ViewUpdate latestViewUpdate;

	public BFTEventPreprocessor(
		BFTEventProcessor forwardTo,
		BFTSyncer bftSyncer,
		ViewUpdate initialViewUpdate
	) {
		this.bftSyncer = Objects.requireNonNull(bftSyncer);
		this.forwardTo = forwardTo;
		this.latestViewUpdate = Objects.requireNonNull(initialViewUpdate);
	}

	@Override
	public void processViewUpdate(ViewUpdate viewUpdate) {
		final View previousView = this.latestViewUpdate.getCurrentView();
		log.trace("Processing viewUpdate {} cur {}", viewUpdate, previousView);

		// FIXME: Check is required for now since Deterministic tests can randomize local messages
		if (viewUpdate.getCurrentView().gt(previousView)) {
			this.latestViewUpdate = viewUpdate;
			forwardTo.processViewUpdate(viewUpdate);
			viewQueues.getOrDefault(viewUpdate.getCurrentView(), new LinkedList<>())
					.forEach(this::processViewCachedEvent);
			viewQueues.keySet().removeIf(v -> v.lte(viewUpdate.getCurrentView()));

			syncingEvents.removeIf(e -> e.getView().lt(viewUpdate.getCurrentView()));
		}
	}

	private void processViewCachedEvent(ConsensusEvent event) {
		if (event instanceof Proposal) {
			log.trace("Processing cached proposal {}", event);
			processProposal((Proposal) event);
		} else if (event instanceof Vote) {
			log.trace("Processing cached vote {}", event);
			processVote((Vote) event);
		} else {
			log.error("Ignoring cached ConsensusEvent {}", event);
		}
	}

	@Override
	public void processBFTUpdate(BFTInsertUpdate update) {
		HashCode vertexId = update.getInserted().getId();
		log.trace("LOCAL_SYNC: {}", vertexId);

		syncingEvents.stream()
			.filter(e -> e.highQC().highestQC().getProposed().getVertexId().equals(vertexId))
			.forEach(this::processQueuedConsensusEvent);

		syncingEvents.removeIf(e -> e.highQC().highestQC().getProposed().getVertexId().equals(vertexId));

		forwardTo.processBFTUpdate(update);
	}

	@Override
	public void processBFTRebuildUpdate(BFTRebuildUpdate rebuildUpdate) {
		rebuildUpdate.getVertexStoreState().getVertices().forEach(v -> {
			HashCode vertexId = v.getId();
			syncingEvents.stream()
				.filter(e -> e.highQC().highestQC().getProposed().getVertexId().equals(vertexId))
				.forEach(this::processQueuedConsensusEvent);

			syncingEvents.removeIf(e -> e.highQC().highestQC().getProposed().getVertexId().equals(vertexId));
		});
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("Vote: PreProcessing {}", vote);
		if (!processVoteInternal(vote)) {
			log.debug("Vote: Queuing {}, waiting for Sync", vote);
			syncingEvents.add(vote);
		}
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("Proposal: PreProcessing {}", proposal);
		if (!processProposalInternal(proposal)) {
			log.debug("Proposal: Queuing {}, waiting for Sync", proposal);
			syncingEvents.add(proposal);
		}
	}

	@Override
	public void processLocalTimeout(ScheduledLocalTimeout scheduledLocalTimeout) {
		forwardTo.processLocalTimeout(scheduledLocalTimeout);
	}

	@Override
	public void start() {
		forwardTo.start();
	}

	private boolean processQueuedConsensusEvent(ConsensusEvent event) {
		if (event == null) {
			return false;
		}

		// Explicitly using switch case method here rather than functional method
		// to process these events due to much better performance
		if (event instanceof Proposal) {
			final Proposal proposal = (Proposal) event;
			return processProposalInternal(proposal);
		}

		if (event instanceof Vote) {
			final Vote vote = (Vote) event;
			return processVoteInternal(vote);
		}

		throw new IllegalStateException("Unexpected consensus event: " + event);
	}

	private boolean processVoteInternal(Vote vote) {
		final View currentView = this.latestViewUpdate.getCurrentView();
		if (vote.getView().gte(currentView)) {
			log.trace("Vote: PreProcessing {}", vote);
			return syncUp(
				"process vote",
				vote.highQC(),
				vote.getAuthor(),
				() -> processOnCurrentViewOrCache(vote, forwardTo::processVote)
			);
		} else {
			log.trace("Vote: Ignoring for past view {}, current view is {}", vote, currentView);
			return true;
		}
	}

	private boolean processProposalInternal(Proposal proposal) {
		final View currentView = this.latestViewUpdate.getCurrentView();
		if (proposal.getView().gte(currentView)) {
			log.trace("Proposal: PreProcessing {}", proposal);
			return syncUp(
				"process proposal",
				proposal.highQC(),
				proposal.getAuthor(),
				() -> processOnCurrentViewOrCache(proposal, forwardTo::processProposal)
			);
		} else {
			log.trace("Proposal: Ignoring for past view {}, current view is {}", proposal, currentView);
			return true;
		}
	}

	private <T extends ConsensusEvent> void processOnCurrentViewOrCache(T event, Consumer<T> processFn) {
		if (latestViewUpdate.getCurrentView().equals(event.getView())) {
			processFn.accept(event);
		} else if (latestViewUpdate.getCurrentView().lt(event.getView())) {
			log.trace("Caching {}, current view is {}", event, latestViewUpdate.getCurrentView());
			viewQueues.putIfAbsent(event.getView(), new LinkedList<>());
			viewQueues.get(event.getView()).add(event);
		} else {
			log.debug("Ignoring {} for past view", event);
		}
	}

	private boolean syncUp(String reason, HighQC highQC, BFTNode author, Runnable whenSynced) {
		SyncResult syncResult = this.bftSyncer.syncToQC(reason, highQC, author);

		switch (syncResult) {
			case SYNCED:
				// if already end of epoch then don't need to process
				// TODO: need to do the same checks on pacemaker side
				// TODO: move this to an epoch preprocessor
				final boolean endOfEpoch = highQC.highestCommittedQC()
					.getCommittedAndLedgerStateProof()
					.orElseThrow(() -> new IllegalStateException("Invalid High QC")).getSecond().isEndOfEpoch();
				if (!endOfEpoch) {
					whenSynced.run();
				}

				return true;
			case INVALID:
				return true;
			case IN_PROGRESS:
				return false;
			default:
				throw new IllegalStateException("Unknown syncResult " + syncResult);
		}
	}
}
