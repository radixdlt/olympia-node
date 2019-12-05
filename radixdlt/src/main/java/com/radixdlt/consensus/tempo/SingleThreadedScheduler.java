package com.radixdlt.consensus.tempo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SingleThreadedScheduler implements Scheduler {
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	@Override
	public Cancellable schedule(Runnable command, long delay, TimeUnit unit) {
		ScheduledFuture<?> future = executor.schedule(command, delay, unit);
		return new Cancellable() {
			@Override
			public boolean cancel() {
				return future.cancel(false);
			}

			@Override
			public boolean isTerminated() {
				return future.isCancelled() || future.isDone();
			}
		};
	}
}
