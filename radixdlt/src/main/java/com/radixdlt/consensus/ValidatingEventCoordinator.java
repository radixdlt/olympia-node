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
import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyViolationException;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.utils.Longs;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Objects;
import java.util.Optional;

/**
 * Processes BFT events with correct validation logic and message sending
 */
public final class ValidatingEventCoordinator implements EventCoordinator {
	private static final Logger log = Logging.getLogger("EC");

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
		ValidatorSet validatorSet
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
	}

	private String getShortName(EUID euid) {
		return euid.toString().substring(0, 6);
	}

	private String getShortName() {
		return getShortName(selfKey.getUID());
	}

	private void startQuorumNewView(View view) {
		// only do something if we're actually the leader
		if (!Objects.equals(proposerElection.getProposer(view), selfKey.getPublicKey())) {
			return;
		}

		Vertex proposal = proposalGenerator.generateProposal(view);
		log.info(getShortName() + ": Broadcasting PROPOSAL: " + proposal);
		this.networkSender.broadcastProposal(proposal);
	}

	private void proceedToView(View nextView) {
		try {
			// TODO make signing more robust by including author in signed hash
			ECDSASignature signature = this.selfKey.sign(Hash.hash256(Longs.toByteArray(nextView.number())));
			NewView newView = new NewView(selfKey.getPublicKey(), nextView, this.vertexStore.getHighestQC(), signature);
			ECPublicKey nextLeader = this.proposerElection.getProposer(nextView);
			log.info(String.format("%s: Sending NEW_VIEW to %s: %s", this.getShortName(), this.getShortName(nextLeader.getUID()), newView));
			this.networkSender.sendNewView(newView, nextLeader.getUID());
		} catch (CryptoException e) {
			throw new IllegalStateException("Failed to sign new view", e);
		}
	}

	private void processQC(QuorumCertificate qc) throws SyncException {
		// sync up to QC if necessary
		this.vertexStore.syncToQC(qc);

		// commit any newly committable vertices
		this.safetyRules.process(qc)
			.ifPresent(vertexId -> {
				final Vertex vertex = vertexStore.commitVertex(vertexId);

				log.info(this.getShortName() + ": Committed vertex: " + vertex);

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
		log.info(this.getShortName() + ": VOTE: Processing " + vote);

		// only do something if we're actually the leader for the vote
		final View view = vote.getVertexMetadata().getView();
		if (!Objects.equals(proposerElection.getProposer(view), selfKey.getPublicKey())) {
			log.warn(String.format("%s: VOTE: Ignoring confused vote %s for %s",
				getShortName(), vote.hashCode(), vote.getVertexMetadata().getView()));
			return;
		}

		// accumulate votes into QCs in store
		Optional<QuorumCertificate> potentialQc = this.pendingVotes.insertVote(vote, validatorSet);
		if (potentialQc.isPresent()) {
			QuorumCertificate qc = potentialQc.get();
			log.info(this.getShortName() + ": VOTE: Formed QC: " + qc);
			try {
				this.processQC(qc);
			} catch (SyncException e) {
				// Should never go here
				throw new IllegalStateException("Could not process QC " + e.getQC() + " which was created.");
			}
		}
	}

	@Override
	public void processNewView(NewView newView) {
		log.info(this.getShortName() + ": NEW_VIEW: Processing: " + newView);

		// only do something if we're actually the leader for the view
		final View view = newView.getView();
		if (!Objects.equals(proposerElection.getProposer(view), selfKey.getPublicKey())) {
			log.warn(String.format("%s: NEW_VIEW: Got confused new-view %s for view %s", this.getShortName(), newView.hashCode(), newView.getView()));
			return;
		}

		try {
			this.processQC(newView.getQC());
		} catch (SyncException e) {
			log.warn(this.getShortName() + ": NEW_VIEW: Ignoring new view because unable to sync to QC " + e.getQC());
			return;
		}

		this.pacemaker.processNewView(newView, validatorSet)
			.ifPresent(this::startQuorumNewView);
	}

	@Override
	public void processProposal(Vertex proposedVertex) {
		log.info(this.getShortName() + ": PROPOSAL: Processing " + proposedVertex);

		final View currentView = this.pacemaker.getCurrentView();
		if (proposedVertex.getView().compareTo(currentView) < 0) {
			log.info(this.getShortName() + ": PROPOSAL: Ignoring view " + proposedVertex.getView() + " Current is: " + currentView);
			return;
		}

		try {
			processQC(proposedVertex.getQC());
		} catch (SyncException e) {
			log.warn(this.getShortName() + ": PROPOSAL: Ignoring because unable to sync to QC " + e.getQC());
			return;
		}

		// TODO: Sync at this point

		final View updatedView = this.pacemaker.getCurrentView();
		if (proposedVertex.getView().compareTo(updatedView) != 0) {
			log.info(this.getShortName() + ": PROPOSAL: Ignoring view " + proposedVertex.getView() + " Current is: " + updatedView);
			return;
		}

		try {
			vertexStore.insertVertex(proposedVertex);
		} catch (VertexInsertionException e) {
			log.info(this.getShortName() + ": PROPOSAL: Rejected: " + e);

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
			log.info(this.getShortName() + ": PROPOSAL: Sending VOTE to " + this.getShortName(leader.getUID()) + ": " + vote);
			networkSender.sendVote(vote, leader.getUID());
		} catch (SafetyViolationException e) {
			log.error(this.getShortName() + ": PROPOSAL: Rejected " + proposedVertex, e);
		}

		// If currently node leader, Proceed to next view
		if (!Objects.equals(proposerElection.getProposer(updatedView), selfKey.getPublicKey())) {
			this.pacemaker.processQC(updatedView)
				.ifPresent(this::proceedToView);
			// TODO: If currently next leader, wait for new view from leader
			//this.proceedToView(updatedView.next());
		}
	}

	@Override
	public void processLocalTimeout(View view) {
		log.info(this.getShortName() + ": LOCAL_TIMEOUT: Processing " + view);

		// proceed to next view if pacemaker feels like it
		this.pacemaker.processLocalTimeout(view)
			.ifPresent(this::proceedToView);
	}
}
