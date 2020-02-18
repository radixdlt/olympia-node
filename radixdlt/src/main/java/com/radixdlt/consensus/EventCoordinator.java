package com.radixdlt.consensus;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;

import java.util.List;

/**
 * Executes consensus logic given events
 */
public final class EventCoordinator {
	private final RadixEngine engine;
	private final Mempool mempool;
	private final NetworkSender networkSender;
	private final Pacemaker pacemaker;

	@Inject
	public EventCoordinator(
		Mempool mempool,
		NetworkSender networkSender,
		Pacemaker pacemaker,
		RadixEngine engine
	) {
		this.mempool = mempool;
		this.networkSender = networkSender;
		this.pacemaker = pacemaker;
		this.engine = engine;
	}

	private void newRound() {
		// I am always the leader, bwahaha!
		List<Atom> atoms = mempool.getAtoms(1, Sets.newHashSet());
		if (!atoms.isEmpty()) {
			networkSender.broadcastProposal(new Vertex(this.pacemaker.getCurrentRound(), atoms.get(0)));
		}
	}

	public void processVote(Vertex vertex) {
		this.pacemaker.processVote(vertex);
		newRound();
	}

	public void processTimeout() {
		this.pacemaker.processTimeout();
		newRound();
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
				networkSender.sendVote(vertex);
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
