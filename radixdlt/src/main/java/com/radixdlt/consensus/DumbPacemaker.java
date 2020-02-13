package com.radixdlt.consensus;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class DumbPacemaker implements Pacemaker {
	private AtomicReference<Consumer<Void>> callbackRef;

	public DumbPacemaker() {
		this.callbackRef = new AtomicReference<>();
	}

	public void run() {
		while (true) {
			try {
				Thread.sleep(200);
				Consumer<Void> callback = callbackRef.get();
				if (callback != null) {
					callback.accept(null);
				}
			} catch (InterruptedException e) {
			}
		}
	}

	public void addCallback(Consumer<Void> callback) {
		this.callbackRef.set(callback);
	}
}
