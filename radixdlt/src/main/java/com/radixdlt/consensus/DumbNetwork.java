package com.radixdlt.consensus;

import com.radixdlt.common.Atom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class DumbNetwork implements NetworkDispatch, Network {
	private final AtomicReference<Consumer<Atom>> callbackRef;

	public DumbNetwork() {
		this.callbackRef = new AtomicReference<>();
	}

	@Override
	public void broadcastProposal(Atom atom) {
		Consumer<Atom> callback = this.callbackRef.get();
		if (callback != null) {
			callback.accept(atom);
		}
	}

	@Override
	public void addCallback(Consumer<Atom> callback) {
		this.callbackRef.set(callback);
	}
}
