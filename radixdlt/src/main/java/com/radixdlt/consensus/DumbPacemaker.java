package com.radixdlt.consensus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Overly simplistic pacemaker
 */
public final class DumbPacemaker implements Pacemaker, PacemakerRx {
	private final AtomicReference<Consumer<Void>> callbackRef;
	private final AtomicReference<ScheduledFuture<?>> futureRef;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

	public DumbPacemaker() {
		this.callbackRef = new AtomicReference<>();
		this.futureRef = new AtomicReference<>();
	}

	private void scheduleTimeout() {
		ScheduledFuture<?> future = executorService.schedule(() -> {
			Consumer<Void> callback = callbackRef.get();
			if (callback != null) {
				callback.accept(null);
			}
		}, 500, TimeUnit.MILLISECONDS);
		this.futureRef.set(future);
	}

	@Override
	public void processTimeout() {
		scheduleTimeout();
	}

	@Override
	public void processedAtom() {
		// FIXME: For sure there are race conditions here
		ScheduledFuture<?> future = this.futureRef.get();
		future.cancel(false);
		scheduleTimeout();
	}

	@Override
	public void start() {
		scheduleTimeout();
	}

	@Override
	public void addTimeoutCallback(Consumer<Void> callback) {
		this.callbackRef.set(callback);
	}
}
