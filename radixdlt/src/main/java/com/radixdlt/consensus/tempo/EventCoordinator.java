package com.radixdlt.consensus.tempo;

import com.google.inject.Inject;
import com.radixdlt.common.Atom;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.store.LedgerEntry;

public final class EventCoordinator {
	private final RadixEngine engine;
	private final MemPool memPool;
	private final AtomToBinaryConverter atomToBinaryConverter;

	@Inject
	public EventCoordinator(MemPool memPool, RadixEngine engine, AtomToBinaryConverter atomToBinaryConverter) {
		this.memPool = memPool;
		this.engine = engine;
		this.atomToBinaryConverter = atomToBinaryConverter;
	}

	public void newRound() {
		LedgerEntry entry = memPool.takeNextEntry();
		if (entry == null) {
			return;
		}

		Atom atom = atomToBinaryConverter.toAtom(entry.getContent());
		engine.store(atom, new AtomEventListener() { });
	}
}
