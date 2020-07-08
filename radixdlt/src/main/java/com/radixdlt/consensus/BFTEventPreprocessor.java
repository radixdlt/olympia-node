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

package com.radixdlt.consensus;

import com.radixdlt.consensus.SyncQueues.SyncQueue;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.crypto.ECPublicKey;
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

	private final ECPublicKey myKey;
	private final BFTEventProcessor forwardTo;
	private final VertexStore vertexStore;
	private final PacemakerState pacemakerState;
	private final ProposerElection proposerElection;
	private final SyncQueues queues;

	public BFTEventPreprocessor(
		ECPublicKey myKey,
		BFTEventProcessor forwardTo,
		PacemakerState pacemakerState,
		VertexStore vertexStore,
		ProposerElection proposerElection,
		SyncQueues queues
	) {
		this.myKey = Objects.requireNonNull(myKey);
		this.pacemakerState = Objects.requireNonNull(pacemakerState);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.queues = queues;
		this.forwardTo = forwardTo;
	}

	private String getShortName() {
		return myKey.euid().toString().substring(0, 6);
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
		log.info("{}: LOCAL_SYNC: {}", this.getShortName(), vertexId);
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
		log.trace("{}: VOTE: PreProcessing {}", this.getShortName(), vote);

		// only do something if we're actually the leader for the vote
		final View view = vote.getVoteData().getProposed().getView();
		// TODO: currently we don't check view of vote relative to our pacemakerState. This opens
		// TODO: up to dos attacks on calculation of next proposer if ProposerElection is
		// TODO: an expensive operation. Need to figure out a way of mitigating this problem
		// TODO: perhaps through filter views too out of bounds
		if (!Objects.equals(proposerElection.getProposer(view), myKey)) {
			log.warn("{}: VOTE: Ignoring confused vote {} for {}",
				getShortName(), vote.hashCode(), vote.getVoteData().getProposed().getView());
			return;
		}

		forwardTo.processVote(vote);
	}

	private boolean processNewViewInternal(NewView newView) {
		log.trace("{}: NEW_VIEW: PreProcessing {}", getShortName(), newView);

		// only do something if we're actually the leader for the view
		final View view = newView.getView();
		if (!Objects.equals(proposerElection.getProposer(view), myKey)) {
			log.warn("{}: NEW_VIEW: Got confused new-view {} for view {}", getShortName(), newView, newView.getView());
			return true;
		}

		final View currentView = pacemakerState.getCurrentView();
		if (newView.getView().compareTo(currentView) < 0) {
			log.trace("{}: NEW_VIEW: Ignoring {} Current is: {}", getShortName(), newView.getView(), currentView);
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
		log.trace("{}: NEW_VIEW: Queueing {}", this.getShortName(), newView);
		if (queues.isEmptyElseAdd(newView)) {
			if (!processNewViewInternal(newView)) {
				log.info("{}: NEW_VIEW: Queuing {} Waiting for Sync", getShortName(), newView);
				queues.add(newView);
			}
		}
	}

	private boolean processProposalInternal(Proposal proposal) {
		log.trace("{}: PROPOSAL: PreProcessing {}", this.getShortName(), proposal);

		final Vertex proposedVertex = proposal.getVertex();
		final View proposedVertexView = proposedVertex.getView();
		final View currentView = this.pacemakerState.getCurrentView();
		if (proposedVertexView.compareTo(currentView) < 0) {
			log.trace("{}: PROPOSAL: Ignoring view {} Current is: {}", this.getShortName(), proposedVertexView, currentView);
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
		log.trace("{}: PROPOSAL: Queueing {}", this.getShortName(), proposal);
		if (queues.isEmptyElseAdd(proposal)) {
			if (!processProposalInternal(proposal)) {
				log.info("{}: PROPOSAL: Queuing {} Waiting for Sync", getShortName(), proposal);
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
			queues.clear();
			vertexStore.clearSyncs();
		}
	}

	@Override
	public void start() {
		forwardTo.start();
	}
}
