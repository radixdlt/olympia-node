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

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.RequiresSyncConsensusEvent;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.SyncQueues.SyncQueue;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.crypto.Hash;
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
	private final VertexStore vertexStore;
	private final PacemakerState pacemakerState;
	private final ProposerElection proposerElection;
	private final SyncQueues queues;

	public BFTEventPreprocessor(
		BFTNode self,
		BFTEventProcessor forwardTo,
		PacemakerState pacemakerState,
		VertexStore vertexStore,
		ProposerElection proposerElection,
		SyncQueues queues
	) {
		this.self = Objects.requireNonNull(self);
		this.pacemakerState = Objects.requireNonNull(pacemakerState);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.queues = queues;
		this.forwardTo = forwardTo;
	}

	private boolean peekAndExecute(SyncQueue queue, Hash vertexId) {
		final RequiresSyncConsensusEvent event = queue.peek(vertexId);
		if (event == null) {
			return false;
		}

		// Explicitly using switch case method here rather than functional method
		// to process these events due to much better performance
		if (event instanceof NewView) {
			final NewView newView = (NewView) event;
			return this.processNewViewInternal(newView);
		}

		if (event instanceof Proposal) {
			final Proposal proposal = (Proposal) event;
			return this.processProposalInternal(proposal);
		}

		throw new IllegalStateException("Unexpected consensus event: " + event);
	}

	/**
	 * Signal that vertexStore now contains the given vertexId.
	 * Executes events which are dependent on this vertex
	 *
	 * @param vertexId the id of the vertex which is now guaranteed be synced.
	 */
	@Override
	public void processLocalSync(Hash vertexId) {
		log.trace("{}: LOCAL_SYNC: {}", this.self::getSimpleName, () -> vertexId);
		for (SyncQueue queue : queues.getQueues()) {
			if (peekAndExecute(queue, vertexId)) {
				queue.pop();
				while (peekAndExecute(queue, null)) {
					queue.pop();
				}
			}
		}

		forwardTo.processLocalSync(vertexId);
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("{}: VOTE: PreProcessing {}", this.self::getSimpleName, () -> vote);

		// only do something if we're actually the leader for the vote
		final View view = vote.getVoteData().getProposed().getView();
		// TODO: currently we don't check view of vote relative to our pacemakerState. This opens
		// TODO: up to dos attacks on calculation of next proposer if ProposerElection is
		// TODO: an expensive operation. Need to figure out a way of mitigating this problem
		// TODO: perhaps through filter views too out of bounds
		if (!Objects.equals(proposerElection.getProposer(view), this.self)) {
			log.warn("{}: VOTE: Ignoring confused vote {} for {}",
				this.self::getSimpleName, vote::hashCode, vote.getVoteData().getProposed()::getView);
			return;
		}

		forwardTo.processVote(vote);
	}

	private boolean processNewViewInternal(NewView newView) {
		log.trace("{}: NEW_VIEW: PreProcessing {}", this.self::getSimpleName, () -> newView);

		// only do something if we're actually the leader for the view
		final View view = newView.getView();
		if (!Objects.equals(proposerElection.getProposer(view), this.self)) {
			log.warn("{}: NEW_VIEW: Got confused new-view {} for view {}", this.self::getSimpleName, () -> newView, newView::getView);
			return true;
		}

		final View currentView = pacemakerState.getCurrentView();
		if (newView.getView().compareTo(currentView) < 0) {
			log.trace("{}: NEW_VIEW: Ignoring {} Current is: {}", this.self::getSimpleName, newView::getView, () -> currentView);
			return true;
		}

		if (this.vertexStore.syncToQC(newView.getQC(), newView.getCommittedQC(), newView.getAuthor())) {
			forwardTo.processNewView(newView);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void processNewView(NewView newView) {
		log.trace("{}: NEW_VIEW: Queueing {}", this.self::getSimpleName, () -> newView);
		if (queues.isEmptyElseAdd(newView)) {
			if (!processNewViewInternal(newView)) {
				log.debug("{}: NEW_VIEW: Queuing {} Waiting for Sync", this.self::getSimpleName, () -> newView);
				queues.add(newView);
			}
		}
	}

	private boolean processProposalInternal(Proposal proposal) {
		log.trace("{}: PROPOSAL: PreProcessing {}", this.self::getSimpleName, () -> proposal);

		final Vertex proposedVertex = proposal.getVertex();
		final View proposedVertexView = proposedVertex.getView();
		final View currentView = this.pacemakerState.getCurrentView();
		if (proposedVertexView.compareTo(currentView) < 0) {
			log.trace("{}: PROPOSAL: Ignoring view {} Current is: {}", this.self::getSimpleName, () -> proposedVertexView, () -> currentView);
			return true;
		}

		if (this.vertexStore.syncToQC(proposal.getQC(), proposal.getCommittedQC(), proposal.getAuthor())) {
			forwardTo.processProposal(proposal);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("{}: PROPOSAL: Queueing {}", this.self::getSimpleName, () -> proposal);
		if (queues.isEmptyElseAdd(proposal)) {
			if (!processProposalInternal(proposal)) {
				log.debug("{}: PROPOSAL: Queuing {} Waiting for Sync", this.self::getSimpleName, () -> proposal);
				queues.add(proposal);
			}
		}
	}

	@Override
	public void processLocalTimeout(View view) {
		final View curView = this.pacemakerState.getCurrentView();
		forwardTo.processLocalTimeout(view);
		final View nextView = this.pacemakerState.getCurrentView();
		if (!curView.equals(nextView)) {
			log.debug("{}: LOCAL_TIMEOUT: Clearing Queues: {}", this.self::getSimpleName, () -> queues);
			queues.clear();
			vertexStore.clearSyncs();
		}
	}

	@Override
	public void start() {
		forwardTo.start();
	}
}
