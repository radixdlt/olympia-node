package com.radixdlt.consensus;

import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.OptionalLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

/**
 * Overly simplistic pacemaker
 */
public final class PacemakerImpl implements Pacemaker, PacemakerRx {
	private static final Logger log = Logging.getLogger("EC");

	private static final int TIMEOUT_MILLISECONDS = 500;
	private final AtomicReference<LongConsumer> callbackRef;
	private final AtomicReference<ScheduledFuture<?>> futureRef;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

	private long currentRound;
	private long highestQCRound;

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
		return currentRound;
	}

	@Override
	public boolean processLocalTimeout(long round) {
		if (round != this.currentRound) {
			return false;
		}

		scheduleTimeout();
		return true;
	}

	@Override
	public OptionalLong processRemoteNewView(NewView newView) {
		// gather timeouts to form timeout QC
		// TODO assumes single node network for now
		return OptionalLong.of(newView.getRound());
	}

	private void updateHighestQCRound(long round) {
		if (round > highestQCRound) {
			highestQCRound = round;
		}
	}

	@Override
	public OptionalLong processQC(long round) {
		// update
		updateHighestQCRound(round);

		// check if a new round can be started
		long newRound = highestQCRound + 1;
		if (newRound <= currentRound) {
			return OptionalLong.empty();
		}

		// start new round
		currentRound = round;
		ScheduledFuture<?> future = this.futureRef.get();
		future.cancel(false);
		scheduleTimeout();

		return OptionalLong.of(currentRound);
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
