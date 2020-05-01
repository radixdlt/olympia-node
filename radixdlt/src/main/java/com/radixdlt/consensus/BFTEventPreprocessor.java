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
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Preprocesses consensus events and ensures that the vertexStore is synced to
 * the correct state before they get forwarded to the actual state reducer.
 *
 * This class should not be updating any part of the BFT Safety state besides
 * the VertexStore.
 */
public final class BFTEventPreprocessor implements BFTEventProcessor {
	private static final Logger log = LogManager.getLogger();

	private final BFTEventProcessor forwardTo;
	private final VertexStore vertexStore;
	private final PacemakerState pacemakerState;
	private final ProposerElection proposerElection;
	private final SystemCounters counters;
	private final ECPublicKey myKey;
	private final ImmutableMap<ECPublicKey, LinkedList<Function<Hash, Boolean>>> queues;

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

	private void executeOrAddToQueue(ECPublicKey author, Supplier<Boolean> runnable) {
		final LinkedList<Function<Hash, Boolean>> queue = queues.get(author);
		if (!queue.isEmpty()) {
			counters.increment(CounterType.CONSENSUS_EVENTS_QUEUE_REQUIRED);
			queue.addLast(h -> runnable.get());
		} else {
			runnable.get();
			counters.increment(CounterType.CONSENSUS_EVENTS_QUEUE_NOT_REQUIRED);
		}
	}

	private boolean syncAndExecuteOrAddToQueue(QuorumCertificate qc, ECPublicKey author, Runnable runnable) {
		// Remove GetVertex RPC call for now
		//final Completable timeout = this.pacemakerRx.timeout(this.pacemakerState.getCurrentView());
		if (!this.vertexStore.syncToQC(qc/*, author, timeout*/)) {
			final LinkedList<Function<Hash, Boolean>> queue = queues.get(author);
			queue.addFirst(h -> {
				if (h.equals(qc.getProposed().getId())) {
					runnable.run();
					return true;
				}

				return false;
			});
			return false;
		} else {
			runnable.run();
			return true;
		}
	}

	private void syncedVertex(Hash vertexId) {
		queues.forEach((key, queue) -> {
			boolean continuePopping = true;
			while (continuePopping) {
				Function<Hash, Boolean> runnable = queue.peek();
				if (runnable == null || !runnable.apply(vertexId)) {
					continuePopping = false;
				} else {
					queue.pop();
				}
			}
		});
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

	@Override
	public void processNewView(NewView newView) {
		log.trace("{}: NEW_VIEW: Queueing {}", this.getShortName(), newView);

		executeOrAddToQueue(newView.getAuthor(), () -> {
			log.trace("{}: NEW_VIEW: PreProcessing {}", this.getShortName(), newView);

			// only do something if we're actually the leader for the view
			final View view = newView.getView();
			if (!Objects.equals(proposerElection.getProposer(view), myKey)) {
				log.warn("{}: NEW_VIEW: Got confused new-view {} for view {}", this.getShortName(), newView.hashCode(), newView.getView());
				return true;
			}

			final View currentView = this.pacemakerState.getCurrentView();
			if (newView.getView().compareTo(currentView) < 0) {
				log.info("{}: NEW_VIEW: Ignoring {} Current is: {}", this.getShortName(), newView.getView(), currentView);
				return true;
			}

			return syncAndExecuteOrAddToQueue(newView.getQC(), newView.getAuthor(), () -> forwardTo.processNewView(newView));
		});
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("{}: PROPOSAL: Queueing {}", this.getShortName(), proposal);

		executeOrAddToQueue(proposal.getAuthor(), () -> {
			log.trace("{}: PROPOSAL: PreProcessing {}", this.getShortName(), proposal);

			final Vertex proposedVertex = proposal.getVertex();
			final View proposedVertexView = proposedVertex.getView();
			final View currentView = this.pacemakerState.getCurrentView();
			if (proposedVertexView.compareTo(currentView) < 0) {
				log.info("{}: PROPOSAL: Ignoring view {} Current is: {}", this.getShortName(), proposedVertexView, currentView);
				return true;
			}

			return syncAndExecuteOrAddToQueue(proposedVertex.getQC(), proposal.getAuthor(), () -> {
				forwardTo.processProposal(proposal);
				if (vertexStore.getVertex(proposedVertex.getId()) != null) {
					syncedVertex(proposal.getVertex().getId());
				}
			});
		});
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
