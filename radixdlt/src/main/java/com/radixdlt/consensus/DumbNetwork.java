package com.radixdlt.consensus;

import com.radixdlt.common.Atom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Overly simplistic network implementation that just sends messages to itself.
 */
public class DumbNetwork implements NetworkSender, NetworkRx {
	private final AtomicReference<Consumer<Atom>> callbackRef;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

	public DumbNetwork() {
		this.callbackRef = new AtomicReference<>();
	}

	@Override
	public void broadcastProposal(Atom atom) {
		executorService.schedule(() -> {
			Consumer<Atom> callback = this.callbackRef.get();
			if (callback != null) {
				callback.accept(atom);
			}
		}, 200, TimeUnit.MILLISECONDS);
	}

	@Override
	public void addProposalCallback(Consumer<Atom> callback) {
		this.callbackRef.set(callback);
	}
}
