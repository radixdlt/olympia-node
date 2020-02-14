package com.radixdlt.consensus;

import com.google.inject.Inject;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import java.util.List;

/**
 * Executes consensus logic given events
 */
public final class EventCoordinator {
	private final RadixEngine engine;
	private final MemPool memPool;
	private final NetworkSender networkSender;
	private final Pacemaker pacemaker;

	@Inject
	public EventCoordinator(
		MemPool memPool,
		NetworkSender networkSender,
		Pacemaker pacemaker,
		RadixEngine engine
	) {
		this.memPool = memPool;
		this.networkSender = networkSender;
		this.pacemaker = pacemaker;
		this.engine = engine;
	}

	private void newRound() {
		// I am always the leader, bwahaha!
		List<Atom> atoms = memPool.getAtoms(1);
		for (Atom atom : atoms) {
			networkSender.broadcastProposal(atom);
		}
	}

	public void processTimeout() {
		this.pacemaker.processTimeout();
		newRound();
	}

	public void processProposal(Atom atom) {
		engine.store(atom, new AtomEventListener() {
			public void onCMError(Atom atom, CMError error) {
				memPool.removeRejectedAtom(atom);
			}

			public void onStateStore(Atom atom) {
				memPool.removeCommittedAtom(atom);
				pacemaker.processedAtom();
				newRound();
			}

			public void onVirtualStateConflict(Atom atom, DataPointer issueParticle) {
				memPool.removeRejectedAtom(atom);
			}

			public void onStateConflict(Atom atom, DataPointer issueParticle, Atom conflictingAtom) {
				memPool.removeRejectedAtom(atom);
			}

			public void onStateMissingDependency(AID atomId, Particle particle) {
				memPool.removeRejectedAtom(atom);
			}
		});
	}
}
