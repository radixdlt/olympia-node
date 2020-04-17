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
import com.radixdlt.identifiers.EUID;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
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

import io.reactivex.rxjava3.core.Single;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.Optional;

/**
 * Processes BFT events with correct validation logic and message sending
 */
public final class ValidatingEventCoordinator implements EventCoordinator {
	private static final Logger log = LogManager.getLogger("EC");

	private final VertexStore vertexStore;
	private final PendingVotes pendingVotes;
	private final ProposalGenerator proposalGenerator;
	private final Mempool mempool;
	private final EventCoordinatorNetworkSender networkSender;
	private final Pacemaker pacemaker;
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
		log.info("{}: Sending NEW_VIEW to {}: {}", this.getShortName(), this.getShortName(nextLeader.euid()), newView);
		this.networkSender.sendNewView(newView, nextLeader);
	}

	private void syncToQC(QuorumCertificate qc) throws SyncException {
		// sync up to QC if necessary
		try {
			this.vertexStore.syncToQC(qc, hash -> Single.error(new RuntimeException("Could not retrieve vertex " + hash)));
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
		log.info("{}: VOTE: Processing {}", this.getShortName(), vote);

		// only do something if we're actually the leader for the vote
		final View view = vote.getVoteData().getProposed().getView();
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
				this.syncToQC(qc);
			} catch (SyncException e) {
				// Should never go here
				throw new IllegalStateException("Could not process QC " + e.getQC() + " which was created.");
			}
		});
	}

	@Override
	public void processNewView(NewView newView) {
		log.info("{}: NEW_VIEW: Processing: {}", this.getShortName(), newView);

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
			this.syncToQC(newView.getQC());
		} catch (SyncException e) {
			log.warn("{}: NEW_VIEW: Ignoring new view because unable to sync to QC {}", this.getShortName(), e.getQC());
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
		log.info("{}: PROPOSAL: Processing {}", this.getShortName(), proposal);

		final Vertex proposedVertex = proposal.getVertex();
		final View currentView = this.pacemaker.getCurrentView();
		if (proposedVertex.getView().compareTo(currentView) < 0) {
			log.info("{}: PROPOSAL: Ignoring view {} Current is: {}", this.getShortName(), proposedVertex.getView(), currentView);
			return;
		}

		try {
			syncToQC(proposedVertex.getQC());
		} catch (SyncException e) {
			log.warn("{}: PROPOSAL: Ignoring because unable to sync to QC {}", this.getShortName(), e.getQC());
			return;
		}

		final View updatedView = this.pacemaker.getCurrentView();
		if (proposedVertex.getView().compareTo(updatedView) != 0) {
			log.info("{}: PROPOSAL: Ignoring view {} Current is: {}", this.getShortName(), proposedVertex.getView(), updatedView);
			return;
		}

		try {
			vertexStore.insertVertex(proposedVertex);
		} catch (VertexInsertionException e) {
			counters.increment(CounterType.CONSENSUS_REJECTED);

			log.info(this.getShortName() + ": PROPOSAL: Rejected", e);

			// TODO: Better logic for removal on exception
			final Atom atom = proposedVertex.getAtom();
			if (atom != null) {
				mempool.removeRejectedAtom(atom.getAID());
			}
			return;
		}

		try {
			final Vote vote = safetyRules.voteFor(proposedVertex);
			final ECPublicKey leader = this.proposerElection.getProposer(updatedView);
			log.info("{}: PROPOSAL: Sending VOTE to {}: {}", this.getShortName(), this.getShortName(leader.euid()), vote);
			networkSender.sendVote(vote, leader);
		} catch (SafetyViolationException e) {
			log.error(this.getShortName() + ": PROPOSAL: Rejected " + proposedVertex, e);
		}

		// If not currently leader or next leader, Proceed to next view
		if (!Objects.equals(proposerElection.getProposer(updatedView), selfKey.getPublicKey())
			&& !Objects.equals(proposerElection.getProposer(updatedView.next()), selfKey.getPublicKey())) {
			this.pacemaker.processQC(updatedView)
				.ifPresent(this::proceedToView);
		}
	}

	@Override
	public void processLocalTimeout(View view) {
		log.info("{}: LOCAL_TIMEOUT: Processing {}", this.getShortName(), view);

		// proceed to next view if pacemaker feels like it
		Optional<View> nextView = this.pacemaker.processLocalTimeout(view);
		if (nextView.isPresent()) {
			counters.increment(CounterType.CONSENSUS_TIMEOUT);
			this.proceedToView(nextView.get());
			log.info("{}: LOCAL_TIMEOUT: Processed {}", this.getShortName(), view);
		} else {
			log.info("{}: LOCAL_TIMEOUT: Ignoring {}", this.getShortName(), view);
		}
	}

	@Override
	public void start() {
		this.pacemaker.processQC(this.vertexStore.getHighestQC().getView())
			.ifPresent(this::proceedToView);
	}
}
