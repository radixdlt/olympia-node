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
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.ProposerElection;
import java.util.Objects;
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
	private final PacemakerState pacemakerState;
	private final ProposerElection proposerElection;
	private final SyncQueues queues;

	public BFTEventPreprocessor(
		BFTNode self,
		BFTEventProcessor forwardTo,
		PacemakerState pacemakerState,
		BFTSyncer bftSyncer,
		ProposerElection proposerElection,
		SyncQueues queues
	) {
		this.self = Objects.requireNonNull(self);
		this.pacemakerState = Objects.requireNonNull(pacemakerState);
		this.bftSyncer = Objects.requireNonNull(bftSyncer);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.queues = queues;
		this.forwardTo = forwardTo;
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
	public void processBFTUpdate(BFTUpdate update) {
		HashCode vertexId = update.getInsertedVertex().getId();

		log.trace("LOCAL_SYNC: {}", vertexId);
		for (SyncQueue queue : queues.getQueues()) {
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
		if (queues.isEmptyElseAdd(vote) && !processVoteInternal(vote)) {
			log.debug("Vote: Queuing {}, waiting for Sync", vote);
			queues.add(vote);
		}
	}

	@Override
	public void processViewTimeout(ViewTimeout viewTimeout) {
		log.trace("ViewTimeout: PreProcessing {}", viewTimeout);
		if (queues.isEmptyElseAdd(viewTimeout) && !processViewTimeoutInternal(viewTimeout)) {
			log.debug("ViewTimeout: Queuing {}, waiting for Sync", viewTimeout);
			queues.add(viewTimeout);
		}
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("Proposal: PreProcessing {}", proposal);
		if (queues.isEmptyElseAdd(proposal) && !processProposalInternal(proposal)) {
			log.debug("Proposal: Queuing {}, waiting for Sync", proposal);
			queues.add(proposal);
		}
	}

	@Override
	public void processLocalTimeout(View view) {
		final View curView = this.pacemakerState.getCurrentView();
		forwardTo.processLocalTimeout(view);
		final View nextView = this.pacemakerState.getCurrentView();
		if (!curView.equals(nextView)) {
			log.debug("LocalTimeout: Clearing Queues: {}", queues);
			for (SyncQueue queue : queues.getQueues()) {
				if (clearAndExecute(queue, nextView.previous())) {
					queue.pop();
					while (peekAndExecute(queue, null)) {
						queue.pop();
					}
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

		// Only do something if it's a view on or after our current
		if (!onCurrentView("ViewTimeout", viewTimeout.getView(), viewTimeout)) {
			return true;
		}
		return syncUp(viewTimeout.highQC(), viewTimeout.getAuthor(), () -> this.forwardTo.processViewTimeout(viewTimeout));
	}

	private boolean processVoteInternal(Vote vote) {
		log.trace("Vote: PreProcessing {}", vote);

		// Only do something if it's a view on or after our current, and we are the leader for the next view
		if (!checkForCurrentViewAndIAmNextLeader("Vote", vote.getView(), vote)) {
			return true;
		}
		return syncUp(vote.highQC(), vote.getAuthor(), () -> this.forwardTo.processVote(vote));
	}

	private boolean processProposalInternal(Proposal proposal) {
		log.trace("Proposal: PreProcessing {}", proposal);

		// Only do something if it's a view on or after our current
		if (!onCurrentView("Proposal", proposal.getVertex().getView(), proposal)) {
			return true;
		}

		return syncUp(proposal.highQC(), proposal.getAuthor(), () -> forwardTo.processProposal(proposal));
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

	private boolean checkForCurrentViewAndIAmNextLeader(String what, View view, Object thing) {
		return onCurrentView(what, view, thing) && iAmNextLeader(what, view, thing);
	}

	private boolean onCurrentView(String what, View view, Object thing) {
		final View currentView = this.pacemakerState.getCurrentView();
		if (view.compareTo(currentView) < 0) {
			log.trace("{}: Ignoring view {}, current is {}: {}", what, view, currentView, thing);
			return false;
		}
		return true;
	}

	private boolean iAmNextLeader(String what, View view, Object thing) {
		// TODO: currently we don't check view of vote relative to our pacemakerState. This opens
		// TODO: up to dos attacks on calculation of next proposer if ProposerElection is
		// TODO: an expensive operation. Need to figure out a way of mitigating this problem
		// TODO: perhaps through filter views too out of bounds
		BFTNode nextLeader = proposerElection.getProposer(view.next());
		boolean iAmTheNextLeader = Objects.equals(nextLeader, this.self);
		if (!iAmTheNextLeader) {
			log.warn("{}: Confused message for view {} (should be sent to {}, I am {}): {}", what, view, nextLeader, this.self, thing);
			return false;
		}
		return true;
	}
}
