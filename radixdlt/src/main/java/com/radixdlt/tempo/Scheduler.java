package com.radixdlt.tempo;

import java.util.concurrent.TimeUnit;

public interface Scheduler {
	Cancellable schedule(Runnable action, long delay, TimeUnit unit);

//	ScheduledFuture<?> scheduledAtFixedRate(Runnable action, long initialDelay, long recurrentDelay, TimeUnit unit);
	interface Cancellable {
		boolean cancel();

		boolean isTerminated();
	}
}
