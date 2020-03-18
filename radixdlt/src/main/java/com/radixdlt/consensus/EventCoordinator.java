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
import com.radixdlt.consensus.safety.VoteResult;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.EventCoordinatorNetworkSender;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Objects;

/**
 * Executes consensus logic given events
 */
public final class EventCoordinator {
	private static final Logger log = Logging.getLogger("EC");

	private final VertexStore vertexStore;
	private final ProposalGenerator proposalGenerator;
	private final Mempool mempool;
	private final EventCoordinatorNetworkSender networkSender;
	private final Pacemaker pacemaker;
	private final ProposerElection proposerElection;
	private final EUID self;
	private final SafetyRules safetyRules;

	@Inject
	public EventCoordinator(
		ProposalGenerator proposalGenerator,
		Mempool mempool,
		EventCoordinatorNetworkSender networkSender,
		SafetyRules safetyRules,
		Pacemaker pacemaker,
		VertexStore vertexStore,
		ProposerElection proposerElection,
		@Named("self") EUID self
	) {
		this.proposalGenerator = Objects.requireNonNull(proposalGenerator);
		this.mempool = Objects.requireNonNull(mempool);
		this.networkSender = Objects.requireNonNull(networkSender);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.self = Objects.requireNonNull(self);
	}

	private void processNewRound(Round round) {
		log.debug("Processing new round: " +  round);
		// only do something if we're actually the leader
		if (!proposerElection.isValidProposer(self, round)) {
			return;
		}

		Vertex proposal = proposalGenerator.generateProposal(this.pacemaker.getCurrentRound());

		// TODO: Handle empty proposals
		if (proposal.getAtom() != null) {
			this.networkSender.broadcastProposal(proposal);
		}
	}

	public void processVote(Vote vote) {
		// only do something if we're actually the leader for the next round
		if (!proposerElection.isValidProposer(self, vote.getVertexMetadata().getRound().next())) {
			log.warn(String.format("Ignoring confused vote %s for %s", vote.hashCode(), vote.getVertexMetadata().getRound()));
			return;
		}

		// accumulate votes into QCs
		// TODO assumes a single node network for now
		QuorumCertificate qc = new QuorumCertificate(vote, vote.getVertexMetadata());
		this.safetyRules.process(qc);
		this.vertexStore.syncToQC(qc);
		this.pacemaker.processQC(qc.getRound())
			.ifPresent(this::processNewRound);
	}

	public void processLocalTimeout(Round round) {
		if (!this.pacemaker.processLocalTimeout(round)) {
			return;
		}

		this.networkSender.sendNewRound(new NewRound(round.next()));
	}

	public void processRemoteNewRound(NewRound newRound) {
		// only do something if we're actually the leader for the next round
		if (!proposerElection.isValidProposer(self, newRound.getRound())) {
			log.warn(String.format("Got confused new round %s for round ", newRound.hashCode()) + newRound.getRound());
			return;
		}

		this.pacemaker.processRemoteNewRound(newRound)
			.ifPresent(this::processNewRound);
	}

	public void processProposal(Vertex proposedVertex) {
		Atom atom = proposedVertex.getAtom();

		try {
			vertexStore.insertVertex(proposedVertex);
		} catch (VertexInsertionException e) {
			log.info("Rejected vertex insertion " + e);

			// TODO: Better logic for removal on exception
			if (atom != null) {
				mempool.removeRejectedAtom(atom.getAID());
			}
			return;
		}

		final VoteResult voteResult;
		try {
			voteResult = safetyRules.voteFor(proposedVertex);
			final Vote vote = voteResult.getVote();
			networkSender.sendVote(vote);
			voteResult.getCommittedVertexId()
				.ifPresent(vertexId -> {
					log.info("Committed vertex " + vertexId);

					final Vertex vertex = vertexStore.commitVertex(vertexId);
					final Atom committedAtom = vertex.getAtom();
					mempool.removeCommittedAtom(committedAtom.getAID());
				});
		} catch (SafetyViolationException e) {
			log.error("Rejected " + proposedVertex, e);
		}
	}
}
