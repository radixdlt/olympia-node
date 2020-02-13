package com.radixdlt.consensus.tempo;

import com.google.inject.Inject;
import com.radixdlt.common.Atom;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;

public final class EventCoordinator {
	private final RadixEngine engine;

	@Inject
	public EventCoordinator(RadixEngine engine) {
		this.engine = engine;
	}

	public void processProposal(Atom atom) {
		engine.store(atom, new AtomEventListener() { });
	}
}
