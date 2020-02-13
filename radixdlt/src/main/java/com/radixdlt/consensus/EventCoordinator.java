package com.radixdlt.consensus;

import com.google.inject.Inject;
import com.radixdlt.common.Atom;
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
			});
		}
	}
}
