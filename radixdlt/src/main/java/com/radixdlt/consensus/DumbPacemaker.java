package com.radixdlt.consensus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class DumbPacemaker implements Pacemaker {
	private final AtomicReference<Consumer<Void>> callbackRef;
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();

	public DumbPacemaker() {
		this.callbackRef = new AtomicReference<>();
	}

	public void start() {
		executorService.submit(() -> {
			while (true) {
				try {
					TimeUnit.MILLISECONDS.sleep(200);
					Consumer<Void> callback = callbackRef.get();
					if (callback != null) {
						callback.accept(null);
					}
				} catch (InterruptedException e) {
				}
			}
		});
	}

	public void addCallback(Consumer<Void> callback) {
		this.callbackRef.set(callback);
	}
}
