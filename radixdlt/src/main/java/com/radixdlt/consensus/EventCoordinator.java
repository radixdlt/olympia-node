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
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyViolationException;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.EventCoordinatorNetworkSender;
import com.radixdlt.utils.Longs;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Objects;
import java.util.Optional;

/**
 * Executes consensus logic given events
 */
public final class EventCoordinator {
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

	@Inject
	public EventCoordinator(
		ProposalGenerator proposalGenerator,
		Mempool mempool,
		EventCoordinatorNetworkSender networkSender,
		SafetyRules safetyRules,
		Pacemaker pacemaker,
		VertexStore vertexStore,
		PendingVotes pendingVotes,
		ProposerElection proposerElection,
		@Named("self") ECKeyPair selfKey
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
	}

	private void processNewView(View view) {
		// only do something if we're actually the leader
		if (!proposerElection.isValidProposer(selfKey.getUID(), view)) {
			return;
		}

		Vertex proposal = proposalGenerator.generateProposal(this.pacemaker.getCurrentView());

		// TODO: Handle empty proposals
		if (proposal.getAtom() != null) {
			this.networkSender.broadcastProposal(proposal);
			log.info("Starting view " + view + " with proposal " + proposal);
		}
	}

	public void processVote(Vote vote) {
		// only do something if we're actually the leader for the next view
		if (!proposerElection.isValidProposer(selfKey.getUID(), vote.getVertexMetadata().getView().next())) {
			log.warn(String.format("Ignoring confused vote %s for %s", vote.hashCode(), vote.getVertexMetadata().getView()));
			return;
		}

		// accumulate votes into QCs in store
		Optional<QuorumCertificate> potentialQc = this.pendingVotes.insertVote(vote);
		if (potentialQc.isPresent()) {
			QuorumCertificate qc = potentialQc.get();
			processQC(qc);
		}
	}

	private void processQC(QuorumCertificate qc) {
		// sync up to QC if necessary
		this.vertexStore.syncToQC(qc);

		// commit any newly committable vertices
		this.safetyRules.process(qc)
			.ifPresent(vertexId -> {
				log.info("Committed vertex " + vertexId);

				final Vertex vertex = vertexStore.commitVertex(vertexId);
				final Atom committedAtom = vertex.getAtom();
				mempool.removeCommittedAtom(committedAtom.getAID());
			});

		// start new view if pacemaker feels like it
		this.pacemaker.processQC(qc.getView())
			.ifPresent(this::processNewView);
	}

	public void processLocalTimeout(View view) {
		if (!this.pacemaker.processLocalTimeout(view)) {
			return;
		}

		try {
			// TODO make signing more robust by including author in signed hash
			ECDSASignature signature = this.selfKey.sign(Hash.hash256(Longs.toByteArray(view.next().number())));
			View nextView = this.pacemaker.getCurrentView();
			NewView newView = new NewView(selfKey.getPublicKey(), nextView, signature);
			this.networkSender.sendNewView(newView, this.proposerElection.getProposer(nextView));
		} catch (CryptoException e) {
			throw new IllegalStateException("Failed to sign new view at " + view, e);
		}
	}

	public void processRemoteNewView(NewView newView) {
		// only do something if we're actually the leader for the next view
		if (!proposerElection.isValidProposer(selfKey.getPublicKey().getUID(), newView.getView())) {
			log.warn(String.format("Got confused new-view %s for view ", newView.hashCode()) + newView.getView());
			return;
		}

		this.pacemaker.processRemoteNewView(newView)
			.ifPresent(this::processNewView);
	}

	public void processProposal(Vertex proposedVertex) {
		processQC(proposedVertex.getQC());

		try {
			vertexStore.insertVertex(proposedVertex);
		} catch (VertexInsertionException e) {
			log.info("Rejected vertex insertion " + e);

			// TODO: Better logic for removal on exception
			final Atom atom = proposedVertex.getAtom();
			if (atom != null) {
				mempool.removeRejectedAtom(atom.getAID());
			}
			return;
		}

		try {
			final Vote vote = safetyRules.voteFor(proposedVertex);
			networkSender.sendVote(vote);
		} catch (SafetyViolationException e) {
			log.error("Rejected " + proposedVertex, e);
		}
	}
}
