package com.radixdlt.consensus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

/**
 * Overly simplistic pacemaker
 */
public final class PacemakerImpl implements Pacemaker, PacemakerRx {
	private static final int TIMEOUT_MILLISECONDS = 500;
	private final AtomicReference<LongConsumer> callbackRef;
	private final AtomicReference<ScheduledFuture<?>> futureRef;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
	private final AtomicLong currentRound = new AtomicLong();

	public PacemakerImpl() {
		this.callbackRef = new AtomicReference<>();
		this.futureRef = new AtomicReference<>();
	}

	private void scheduleTimeout() {
		long currentRound = getCurrentRound();
		ScheduledFuture<?> future = executorService.schedule(() -> {
			LongConsumer callback = callbackRef.get();
			if (callback != null) {
				callback.accept(currentRound);
			}
		}, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
		this.futureRef.set(future);
	}

	@Override
	public long getCurrentRound() {
		return currentRound.get();
	}

	@Override
	public boolean processLocalTimeout(long round) {
		if (round != this.currentRound.get()) {
			return false;
		}

		scheduleTimeout();
		return true;
	}

	@Override
	public void processVertex(Vertex vertex) {
		if (vertex.getRound() < currentRound.get()) {
			return;
		}

		// FIXME: For sure there are race conditions here
		currentRound.getAndIncrement();

		ScheduledFuture<?> future = this.futureRef.get();
		future.cancel(false);
		scheduleTimeout();
	}

	@Override
	public void start() {
		scheduleTimeout();
	}

	@Override
	public void addTimeoutCallback(LongConsumer callback) {
		this.callbackRef.set(callback);
	}
}
