/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyViolationException;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.utils.Longs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.Optional;

/**
 * Processes BFT events with correct validation logic and message sending
 */
public final class ValidatingEventCoordinator implements EventCoordinator {
	private static final Logger log = LogManager.getLogger();

	private final VertexStore vertexStore;
	private final PendingVotes pendingVotes;
	private final ProposalGenerator proposalGenerator;
	private final Mempool mempool;
	private final EventCoordinatorNetworkSender networkSender;
	private final Pacemaker pacemaker;
	private final PacemakerRx pacemakerRx;
	private final ProposerElection proposerElection;
	private final ECKeyPair selfKey; // TODO remove signing/address to separate identity management
	private final SafetyRules safetyRules;
	private final ValidatorSet validatorSet;
	private final SystemCounters counters;

	@Inject
	public ValidatingEventCoordinator(
		ProposalGenerator proposalGenerator,
		Mempool mempool,
		EventCoordinatorNetworkSender networkSender,
		SafetyRules safetyRules,
		Pacemaker pacemaker,
		PacemakerRx pacemakerRx, // TODO: Remove this once non-blocking implemented
		VertexStore vertexStore,
		PendingVotes pendingVotes,
		ProposerElection proposerElection,
		@Named("self") ECKeyPair selfKey,
		ValidatorSet validatorSet,
		SystemCounters counters
	) {
		this.proposalGenerator = Objects.requireNonNull(proposalGenerator);
		this.mempool = Objects.requireNonNull(mempool);
		this.networkSender = Objects.requireNonNull(networkSender);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.pacemakerRx = Objects.requireNonNull(pacemakerRx);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.pendingVotes = Objects.requireNonNull(pendingVotes);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.selfKey = Objects.requireNonNull(selfKey);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.counters = Objects.requireNonNull(counters);
	}

	private String getShortName(EUID euid) {
		return euid.toString().substring(0, 6);
	}

	private String getShortName() {
		return getShortName(selfKey.euid());
	}

	// Hotstuff's Event-Driven OnNextSyncView
	private void proceedToView(View nextView) {
		// TODO make signing more robust by including author in signed hash
		ECDSASignature signature = this.selfKey.sign(Hash.hash256(Longs.toByteArray(nextView.number())));
		NewView newView = new NewView(selfKey.getPublicKey(), nextView, this.vertexStore.getHighestQC(), signature);
		ECPublicKey nextLeader = this.proposerElection.getProposer(nextView);
		log.debug("{}: Sending NEW_VIEW to {}: {}", this.getShortName(), this.getShortName(nextLeader.euid()), newView);
		this.networkSender.sendNewView(newView, nextLeader);
	}

	private void sync(QuorumCertificate qc, ECPublicKey node) throws SyncException {
		// sync up to QC if necessary
		try {
			this.vertexStore.syncToQC(qc, node, this.pacemakerRx.timeout(this.pacemaker.getCurrentView()));
		} catch (SyncException e) {
			counters.increment(CounterType.CONSENSUS_SYNC_EXCEPTION);
			throw e;
		}

		// commit any newly committable vertices
		this.safetyRules.process(qc)
			.ifPresent(vertexId -> {
				final Vertex vertex = vertexStore.commitVertex(vertexId);

				log.info("{}: Committed vertex: {}", this.getShortName(), vertex);

				final Atom committedAtom = vertex.getAtom();
				if (committedAtom != null) {
					mempool.removeCommittedAtom(committedAtom.getAID());
				}
			});

		// proceed to next view if pacemaker feels like it
		this.pacemaker.processQC(qc.getView())
			.ifPresent(this::proceedToView);
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("{}: VOTE: Processing {}", this.getShortName(), vote);

		// only do something if we're actually the leader for the vote
		final View view = vote.getVoteData().getProposed().getView();
		// TODO: currently we don't check view of vote relative to our pacemaker. This opens
		// TODO: up to dos attacks on calculation of next proposer if ProposerElection is
		// TODO: an expensive operation. Need to figure out a way of mitigating this problem
		// TODO: perhaps through filter views too out of bounds
		if (!Objects.equals(proposerElection.getProposer(view), selfKey.getPublicKey())) {
			log.warn("{}: VOTE: Ignoring confused vote {} for {}",
				getShortName(), vote.hashCode(), vote.getVoteData().getProposed().getView());
			return;
		}

		// accumulate votes into QCs in store
		Optional<QuorumCertificate> potentialQc = this.pendingVotes.insertVote(vote, validatorSet);
		potentialQc.ifPresent(qc -> {
			log.info("{}: VOTE: Formed QC: {}", this.getShortName(), qc);
			try {
				this.sync(qc, vote.getAuthor());
			} catch (SyncException e) {
				// Should never go here
				throw new IllegalStateException("Could not process QC " + e.getQC() + " which was created.");
			}
		});
	}

	@Override
	public void processNewView(NewView newView) {
		log.trace("{}: NEW_VIEW: Processing: {}", this.getShortName(), newView);

		final View currentView = this.pacemaker.getCurrentView();
		if (newView.getView().compareTo(currentView) < 0) {
			log.info("{}: NEW_VIEW: Ignoring {} Current is: {}", this.getShortName(), newView.getView(), currentView);
			return;
		}

		// only do something if we're actually the leader for the view
		final View view = newView.getView();
		if (!Objects.equals(proposerElection.getProposer(view), selfKey.getPublicKey())) {
			log.warn("{}: NEW_VIEW: Got confused new-view {} for view {}", this.getShortName(), newView.hashCode(), newView.getView());
			return;
		}

		this.counters.set(CounterType.CONSENSUS_VIEW, newView.getView().number());
		try {
			this.sync(newView.getQC(), newView.getAuthor());
		} catch (SyncException e) {
			log.warn("{}: NEW_VIEW: Ignoring new view because unable to sync to QC {}", this.getShortName(), e.getQC(), e.getCause());
			return;
		}

		this.pacemaker.processNewView(newView, validatorSet)
			.ifPresent(syncedView -> {
				// Hotstuff's Event-Driven OnBeat
				final Vertex proposedVertex = proposalGenerator.generateProposal(view);
				final Proposal proposal = safetyRules.signProposal(proposedVertex);
				log.info("{}: Broadcasting PROPOSAL: {}", getShortName(), proposal);
				this.networkSender.broadcastProposal(proposal);
			});
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("{}: PROPOSAL: Processing {}", this.getShortName(), proposal);

		final Vertex proposedVertex = proposal.getVertex();
		final View proposedVertexView = proposedVertex.getView();
		final View currentView = this.pacemaker.getCurrentView();
		if (proposedVertexView.compareTo(currentView) < 0) {
			log.info("{}: PROPOSAL: Ignoring view {} Current is: {}", this.getShortName(), proposedVertexView, currentView);
			return;
		}

		try {
			sync(proposedVertex.getQC(), proposal.getAuthor());
		} catch (SyncException e) {
			log.warn("{}: PROPOSAL: Ignoring because unable to sync to QC {}", this.getShortName(), e.getQC(), e.getCause());
			return;
		}

		final View updatedView = this.pacemaker.getCurrentView();
		if (proposedVertexView.compareTo(updatedView) != 0) {
			log.info("{}: PROPOSAL: Ignoring view {} Current is: {}", this.getShortName(), proposedVertexView, updatedView);
			return;
		}

		if (!proposedVertex.hasDirectParent()) {
			counters.increment(CounterType.CONSENSUS_INDIRECT_PARENT);
		}

		try {
			vertexStore.insertVertex(proposedVertex);
		} catch (VertexInsertionException e) {
			counters.increment(CounterType.CONSENSUS_REJECTED);

			log.info(String.format("%s: PROPOSAL: Rejected", this.getShortName()), e);

			// TODO: Better logic for removal on exception
			final Atom atom = proposedVertex.getAtom();
			if (atom != null) {
				mempool.removeRejectedAtom(atom.getAID());
			}
			return;
		}

		final ECPublicKey currentLeader = this.proposerElection.getProposer(updatedView);
		try {
			final Vote vote = safetyRules.voteFor(proposedVertex);
			log.debug("{}: PROPOSAL: Sending VOTE to {}: {}", this.getShortName(), this.getShortName(currentLeader.euid()), vote);
			networkSender.sendVote(vote, currentLeader);
		} catch (SafetyViolationException e) {
			log.error(String.format("%s: PROPOSAL: Rejected %s", this.getShortName(), proposedVertex), e);
		}

		// If not currently leader or next leader, Proceed to next view
		if (!Objects.equals(currentLeader, selfKey.getPublicKey())) {
			final ECPublicKey nextLeader = this.proposerElection.getProposer(updatedView.next());
			if (!Objects.equals(nextLeader, selfKey.getPublicKey())) {
				this.pacemaker.processQC(updatedView).ifPresent(this::proceedToView);
			}
		}
	}

	@Override
	public void processLocalTimeout(View view) {
		log.trace("{}: LOCAL_TIMEOUT: Processing {}", this.getShortName(), view);

		// proceed to next view if pacemaker feels like it
		Optional<View> nextView = this.pacemaker.processLocalTimeout(view);
		if (nextView.isPresent()) {
			counters.set(CounterType.CONSENSUS_TIMEOUT_VIEW, view.number());
			counters.increment(CounterType.CONSENSUS_TIMEOUT);
			this.proceedToView(nextView.get());
			log.info("{}: LOCAL_TIMEOUT: Processed {}", this.getShortName(), view);
		} else {
			log.debug("{}: LOCAL_TIMEOUT: Ignoring {}", this.getShortName(), view);
		}
	}

	@Override
	public void processGetVertexRequest(GetVertexRequest request) {
		log.trace("{}: GET_VERTEX Request: Processing: {}", this.getShortName(), request);
		Vertex vertex = this.vertexStore.getVertex(request.getVertexId());
		log.debug("{}: GET_VERTEX Request: Sending Response: {}", this.getShortName(), vertex);
		request.getResponder().accept(vertex);
	}

	@Override
	public void start() {
		this.pacemaker.processQC(this.vertexStore.getHighestQC().getView())
			.ifPresent(this::proceedToView);
	}
}
