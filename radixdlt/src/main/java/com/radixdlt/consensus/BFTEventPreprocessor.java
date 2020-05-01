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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import java.util.LinkedList;
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
 */
public final class BFTEventPreprocessor implements BFTEventProcessor {
	private static final Logger log = LogManager.getLogger();

	private final BFTEventProcessor forwardTo;
	private final VertexStore vertexStore;
	private final PacemakerState pacemakerState;
	private final ProposerElection proposerElection;
	private final SystemCounters counters;
	private final ECPublicKey myKey;
	private final ImmutableMap<ECPublicKey, LinkedList<ConsensusEvent>> queues;

	@Inject
	public BFTEventPreprocessor(
		ECPublicKey myKey,
		BFTEventProcessor forwardTo,
		PacemakerState pacemakerState,
		VertexStore vertexStore,
		ProposerElection proposerElection,
		ValidatorSet validatorSet,
		SystemCounters counters
	) {
		this.myKey = Objects.requireNonNull(myKey);
		this.pacemakerState = Objects.requireNonNull(pacemakerState);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.queues = validatorSet.getValidators().stream()
			.collect(ImmutableMap.toImmutableMap(Validator::nodeKey, v -> new LinkedList<>()));
		this.counters = Objects.requireNonNull(counters);
		this.forwardTo = forwardTo;
	}

	private String getShortName() {
		return myKey.euid().toString().substring(0, 6);
	}

	private boolean peekAndExecute(LinkedList<ConsensusEvent> queue, Hash vertexId) {
		ConsensusEvent event = queue.peek();

		if (event == null) {
			return false;
		}

		// Explicitly using switch case method here rather than functional method
		// to process these events due to much better performance
		if (event instanceof NewView) {
			NewView newView = (NewView) event;
			return newView.getQC().getProposed().getId().equals(vertexId)
				&& this.processNewViewInternal(newView);
		}

		if (event instanceof Proposal) {
			Proposal proposal = (Proposal) event;
			return proposal.getVertex().getQC().getProposed().getId().equals(vertexId)
				&& this.processProposalInternal(proposal);
		}

		throw new IllegalStateException("Unexpected consensus event: " + event);
	}

	private void syncedVertex(Hash vertexId) {
		for (LinkedList<ConsensusEvent> queue : queues.values()) {
			while (peekAndExecute(queue, vertexId)) {
				queue.pop();
			}
		}
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
		}

		forwardTo.processVote(vote);
	}

	private boolean processNewViewInternal(NewView newView) {
		log.trace("{}: NEW_VIEW: PreProcessing {}", getShortName(), newView);

		// only do something if we're actually the leader for the view
		final View view = newView.getView();
		if (!Objects.equals(proposerElection.getProposer(view), myKey)) {
			log.warn("{}: NEW_VIEW: Got confused new-view {} for view {}", getShortName(), newView.hashCode(), newView.getView());
			return true;
		}

		final View currentView = pacemakerState.getCurrentView();
		if (newView.getView().compareTo(currentView) < 0) {
			log.info("{}: NEW_VIEW: Ignoring {} Current is: {}", getShortName(), newView.getView(), currentView);
			return true;
		}

		if (this.vertexStore.syncToQC(newView.getQC())) {
			forwardTo.processNewView(newView);
			return true;
		} else {
			counters.increment(CounterType.CONSENSUS_EVENTS_QUEUED_SYNC);
			final LinkedList<ConsensusEvent> queue = queues.get(newView.getAuthor());
			queue.addFirst(newView);
			return false;
		}
	}

	@Override
	public void processNewView(NewView newView) {
		log.trace("{}: NEW_VIEW: Queueing {}", this.getShortName(), newView);
		final LinkedList<ConsensusEvent> queue = queues.get(newView.getAuthor());
		if (!queue.isEmpty()) {
			counters.increment(CounterType.CONSENSUS_EVENTS_QUEUED_INITIAL);
			queue.addLast(newView);
		} else {
			processNewViewInternal(newView);
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


		if (this.vertexStore.syncToQC(proposedVertex.getQC())) {
			forwardTo.processProposal(proposal);
			if (vertexStore.getVertex(proposedVertex.getId()) != null) {
				syncedVertex(proposal.getVertex().getId());
			}
			return true;
		} else {
			final LinkedList<ConsensusEvent> queue = queues.get(proposal.getAuthor());
			counters.increment(CounterType.CONSENSUS_EVENTS_QUEUED_SYNC);
			queue.addFirst(proposal);
			return false;
		}
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("{}: PROPOSAL: Queueing {}", this.getShortName(), proposal);

		final LinkedList<ConsensusEvent> queue = queues.get(proposal.getAuthor());
		if (!queue.isEmpty()) {
			counters.increment(CounterType.CONSENSUS_EVENTS_QUEUED_INITIAL);
			queue.addLast(proposal);
		} else {
			processProposalInternal(proposal);
		}
	}

	@Override
	public void processLocalTimeout(View view) {
		final View curView = this.pacemakerState.getCurrentView();
		forwardTo.processLocalTimeout(view);
		final View nextView = this.pacemakerState.getCurrentView();
		if (!curView.equals(nextView)) {
			// Could probably forward some of these but don't worry for now
			queues.values().forEach(LinkedList::clear);
		}
	}

	@Override
	public void processGetVertexRequest(GetVertexRequest request) {
		forwardTo.processGetVertexRequest(request);
	}

	@Override
	public void start() {
		forwardTo.start();
	}
}
