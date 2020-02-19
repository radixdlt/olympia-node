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

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import java.util.List;
import java.util.Objects;

/**
 * Executes consensus logic given events
 */
public final class EventCoordinator {
	private static final Logger log = Logging.getLogger("EC");

	private final VertexStore vertexStore;
	private final RadixEngine engine;
	private final Mempool mempool;
	private final NetworkSender networkSender;
	private final Pacemaker pacemaker;
	private final ProposerElection proposerElection;
	private final EUID self;
	private final SafetyRules safetyRules;

	@Inject
	public EventCoordinator(
		Mempool mempool,
		NetworkSender networkSender,
		SafetyRules safetyRules,
		Pacemaker pacemaker,
		VertexStore vertexStore,
		RadixEngine engine,
		ProposerElection proposerElection,
		@Named("self") EUID self
	) {
		this.mempool = Objects.requireNonNull(mempool);
		this.networkSender = Objects.requireNonNull(networkSender);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.vertexStore = Objects.requireNonNull(vertexStore);
        this.engine = Objects.requireNonNull(engine);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.self = Objects.requireNonNull(self);
	}

	private void processNewRound(long round) {
		log.debug(String.format("Processing new round %d", round));
		// only do something if we're actually the leader
		if (!proposerElection.isValidProposer(self, round)) {
			return;
		}
        
		List<Atom> atoms = mempool.getAtoms(1, Sets.newHashSet());
		if (!atoms.isEmpty()) {
			QuorumCertificate highestQC = vertexStore.getHighestQC();
			log.info("Starting round " + round + " with proposal " + atoms.get(0));
			networkSender.broadcastProposal(new Vertex(highestQC, this.pacemaker.getCurrentRound(), atoms.get(0)));
		}
	}

	public void processVote(Vote vote) {
		// only do something if we're actually the leader for the next round
		if (!proposerElection.isValidProposer(self, vote.getRound() + 1)) {
			log.warn(String.format("Got confused vote %s for round %d", vote.hashCode(), vote.getRound()));
			return;
		}

		// accumulate votes into QCs
		// TODO assumes a single node network for now
		QuorumCertificate qc = new QuorumCertificate(vote);
		this.vertexStore.syncToQC(qc);
		this.pacemaker.processQC(qc.getRound())
			.ifPresent(this::processNewRound);
	}

	public void processLocalTimeout(long round) {
		if (!this.pacemaker.processLocalTimeout(round)) {
			return;
		}

		this.networkSender.sendNewView(new NewRound(round + 1));
	}

	public void processRemoteNewRound(NewRound newRound) {
		// only do something if we're actually the leader for the next round
		if (!proposerElection.isValidProposer(self, newRound.getRound())) {
			log.warn(String.format("Got confused new round %s for round %d", newRound.hashCode(), newRound.getRound()));
			return;
		}

		this.pacemaker.processRemoteNewRound(newRound)
			.ifPresent(this::processNewRound);
	}

	public void processProposal(Vertex vertex) {
		Atom atom = vertex.getAtom();

		// TODO: Fix this interface
		engine.store(atom, new AtomEventListener() {
			@Override
			public void onCMError(Atom atom, CMError error) {
				mempool.removeRejectedAtom(atom.getAID());
			}

			@Override
			public void onStateStore(Atom atom) {
				mempool.removeCommittedAtom(atom.getAID());

				vertexStore.insertVertex(vertex);

				final Vote vote = safetyRules.vote(vertex);

				networkSender.sendVote(vote);
			}

			@Override
			public void onVirtualStateConflict(Atom atom, DataPointer issueParticle) {
				mempool.removeRejectedAtom(atom.getAID());
			}

			@Override
			public void onStateConflict(Atom atom, DataPointer issueParticle, Atom conflictingAtom) {
				mempool.removeRejectedAtom(atom.getAID());
			}

			@Override
			public void onStateMissingDependency(AID atomId, Particle particle) {
				mempool.removeRejectedAtom(atom.getAID());
			}
		});
	}
}
