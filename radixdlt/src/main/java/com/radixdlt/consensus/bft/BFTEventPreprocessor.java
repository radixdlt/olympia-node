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
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTSyncer.SyncResult;
import com.radixdlt.consensus.bft.SyncQueues.SyncQueue;

import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

	private final BFTNode self;
	private final BFTEventProcessor forwardTo;
	private final BFTSyncer bftSyncer;
	private final SyncQueues syncQueues;

	private final Map<View, List<ConsensusEvent>> viewQueues = new HashMap<>();
	private ViewUpdate latestViewUpdate;

	public BFTEventPreprocessor(
		BFTNode self,
		BFTEventProcessor forwardTo,
		BFTSyncer bftSyncer,
		SyncQueues syncQueues,
		ViewUpdate initialViewUpdate
	) {
		this.self = Objects.requireNonNull(self);
		this.bftSyncer = Objects.requireNonNull(bftSyncer);
		this.syncQueues = syncQueues;
		this.forwardTo = forwardTo;
		this.latestViewUpdate = Objects.requireNonNull(initialViewUpdate);
	}

	// TODO: Cleanup
	// TODO: remove queues and treat each message independently
	private boolean clearAndExecute(SyncQueue queue, View view) {
		final ConsensusEvent event = queue.clearViewAndGetNext(view);
		return processQueuedConsensusEvent(event);
	}

	private boolean peekAndExecute(SyncQueue queue, HashCode vertexId) {
		final ConsensusEvent event = queue.peek(vertexId);
		return processQueuedConsensusEvent(event);
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
		}
	}

	private void processViewCachedEvent(ConsensusEvent event) {
		if (event instanceof Proposal) {
			log.trace("Processing cached proposal {}", event);
			processProposal((Proposal) event);
		} else if (event instanceof Vote) {
			log.trace("Processing cached vote {}", event);
			processVote((Vote) event);
		} else if (event instanceof ViewTimeout) {
			log.trace("Processing cached view timeout {}", event);
			processViewTimeout((ViewTimeout) event);
		} else {
			log.error("Ignoring cached ConsensusEvent {}", event);
		}
	}

	@Override
	public void processBFTUpdate(BFTUpdate update) {
		HashCode vertexId = update.getInsertedVertex().getId();

		log.trace("LOCAL_SYNC: {}", update.getInsertedVertex());
		for (SyncQueue queue : syncQueues.getQueues()) {
			if (peekAndExecute(queue, vertexId)) {
				queue.pop();
				while (peekAndExecute(queue, null)) {
					queue.pop();
				}
			}
		}

		forwardTo.processBFTUpdate(update);
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("Vote: PreProcessing {}", vote);
		if (syncQueues.isEmptyElseAdd(vote) && !processVoteInternal(vote)) {
			log.debug("Vote: Queuing {}, waiting for Sync", vote);
			syncQueues.add(vote);
		}
	}

	@Override
	public void processViewTimeout(ViewTimeout viewTimeout) {
		log.trace("ViewTimeout: PreProcessing {}", viewTimeout);
		if (syncQueues.isEmptyElseAdd(viewTimeout) && !processViewTimeoutInternal(viewTimeout)) {
			log.debug("ViewTimeout: Queuing {}, waiting for Sync", viewTimeout);
			syncQueues.add(viewTimeout);
		}
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("Proposal: PreProcessing {}", proposal);
		if (syncQueues.isEmptyElseAdd(proposal) && !processProposalInternal(proposal)) {
			log.debug("Proposal: Queuing {}, waiting for Sync", proposal);
			syncQueues.add(proposal);
		}
	}

	@Override
	public void processLocalTimeout(ScheduledLocalTimeout scheduledLocalTimeout) {
		forwardTo.processLocalTimeout(scheduledLocalTimeout);

		View view = scheduledLocalTimeout.view();

		if (!view.equals(this.latestViewUpdate.getCurrentView())) {
			return;
		}

		// TODO: check if this is correct; move to processViewUpdate?
		log.debug("LocalTimeout: Clearing Queues: {}", syncQueues);
		for (SyncQueue queue : syncQueues.getQueues()) {
			if (clearAndExecute(queue, view)) {
				queue.pop();
				while (peekAndExecute(queue, null)) {
					queue.pop();
				}
			}
		}
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

		if (event instanceof ViewTimeout) {
			final ViewTimeout viewTimeout = (ViewTimeout) event;
			return processViewTimeoutInternal(viewTimeout);
		}

		throw new IllegalStateException("Unexpected consensus event: " + event);
	}

	private boolean processViewTimeoutInternal(ViewTimeout viewTimeout) {
		log.trace("ViewTimeout: PreProcessing {}", viewTimeout);

		// Only sync and execute if it's a view on or after our current
		if (!onCurrentView("ViewTimeout", viewTimeout.getView(), viewTimeout)) {
			return true;
		}

		return syncUp(
			viewTimeout.highQC(),
			viewTimeout.getAuthor(),
			() -> processOnCurrentViewOrCache(viewTimeout, forwardTo::processViewTimeout)
		);
	}

	private boolean processVoteInternal(Vote vote) {
		log.trace("Vote: PreProcessing {}", vote);

		// Only do something if it's a view on or after our current, and we are the leader for the next view
		if (!onCurrentView("Vote", vote.getView(), vote)) {
			return true;
		}
		return syncUp(
			vote.highQC(),
			vote.getAuthor(),
			() -> processOnCurrentViewOrCache(vote, v -> {
				if (iAmNextLeader(v)) {
					forwardTo.processVote(v);
				}
			})
		);
	}

	private boolean processProposalInternal(Proposal proposal) {
		log.trace("Proposal: PreProcessing {}", proposal);

		// Only do something if it's a view on or after our current
		if (!onCurrentView("Proposal", proposal.getVertex().getView(), proposal)) {
			return true;
		}

		return syncUp(
			proposal.highQC(),
			proposal.getAuthor(),
			() -> processOnCurrentViewOrCache(proposal, forwardTo::processProposal)
		);
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

	private boolean syncUp(HighQC highQC, BFTNode author, Runnable whenSynced) {
		SyncResult syncResult = this.bftSyncer.syncToQC(highQC, author);

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

	private boolean onCurrentView(String what, View view, Object thing) {
		final View currentView = this.latestViewUpdate.getCurrentView();
		if (view.compareTo(currentView) < 0) {
			log.trace("{}: Ignoring view {}, current is {}: {}", what, view, currentView, thing);
			return false;
		}
		return true;
	}

	private boolean iAmNextLeader(Object thing) {
		// TODO: currently we don't check view of vote relative to our pacemakerState. This opens
		// TODO: up to dos attacks on calculation of next proposer if ProposerElection is
		// TODO: an expensive operation. Need to figure out a way of mitigating this problem
		// TODO: perhaps through filter views too out of bounds
		BFTNode nextLeader = this.latestViewUpdate.getNextLeader();
		boolean iAmTheNextLeader = Objects.equals(nextLeader, this.self);
		if (!iAmTheNextLeader) {
			log.warn("Confused message for view {} (should be sent to {}, I am {}): {}",
				this.latestViewUpdate.getCurrentView(),
				nextLeader,
				this.self,
				thing
			);
			return false;
		}
		return true;
	}
}
