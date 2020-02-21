package com.radixdlt.consensus;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.OptionalLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Overly simplistic pacemaker
 */
public final class PacemakerImpl implements Pacemaker, PacemakerRx {
	private static final Logger log = Logging.getLogger("EC");

	static final int TIMEOUT_MILLISECONDS = 500;
	private final PublishSubject<Long> timeouts;
	private ScheduledFuture<?> currentTimeout;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

	private long currentRound;
	private long highestQCRound;

	public PacemakerImpl() {
		this.timeouts = PublishSubject.create();
	}

	private void scheduleTimeout() {
		final long currentRound = this.currentRound;
		if (this.currentTimeout != null) {
			this.currentTimeout.cancel(false);
		}

		this.currentTimeout = executorService.schedule(() -> {
			timeouts.onNext(currentRound);
		}, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
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

		this.currentRound++;

		scheduleTimeout();
		return true;
	}

	@Override
	public OptionalLong processRemoteNewRound(NewRound newRound) {
		// gather new rounds to form new round QC
		// TODO assumes single node network for now
		return OptionalLong.of(newRound.getRound());
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
		currentRound = newRound;

		scheduleTimeout();

		return OptionalLong.of(currentRound);
	}

	@Override
	public void start() {
		scheduleTimeout();
	}

	@Override
	public Observable<Long> localTimeouts() {
		return timeouts;
	}
}
