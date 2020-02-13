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

public final class EventCoordinator {
	private final RadixEngine engine;
	private final MemPool memPool;

	@Inject
	public EventCoordinator(MemPool memPool, RadixEngine engine) {
		this.memPool = memPool;
		this.engine = engine;
	}

	public void newRound() {
		List<Atom> atoms = memPool.getAtoms(1);
		for (Atom atom : atoms) {
			engine.store(atom, new AtomEventListener() {
				public void onStateStore(Atom atom) {
					memPool.removeCommittedAtom(atom);
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
}
