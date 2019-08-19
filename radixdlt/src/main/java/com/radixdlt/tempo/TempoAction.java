package com.radixdlt.tempo;

import com.radixdlt.tempo.actions.control.RepeatScheduleAction;
import com.radixdlt.tempo.actions.control.ScheduleAction;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * Marker interface for Tempo actions
 */
public interface TempoAction {
	// TODO provide and use default timeout / retry

	/**
	 * Schedule this action to be dispatched after some delay
	 * @param delay The delay
	 * @param unit The unit of the delay
	 * @return A scheduled action
	 */
	default ScheduleAction delay(long delay, TimeUnit unit) {
		return new ScheduleAction(this, delay, unit);
	}

	/**
	 * Schedule an action to be dispatched repeatedly with some delay after some delay
	 * @param initialDelay The initial delay
	 * @param recurrentDelay The recurrent delay
	 * @param unit The unit of the delays
	 * @return A repeatedly scheduled action
	 */
	default RepeatScheduleAction repeat(long initialDelay, long recurrentDelay, TimeUnit unit) {
		return new RepeatScheduleAction(this, initialDelay, recurrentDelay, unit);
	}

	/**
	 * Repeat the given action for a certain number of seconds until the termination condition is satisfied
	 * @param delaySeconds The delay between repetitions in seconds
	 * @param shouldTerminate The condition for termination
	 * @return A conditionally repeatedly scheduled action
	 */
	default RepeatScheduleAction repeatUntil(long delaySeconds, BooleanSupplier shouldTerminate) {
		return new RepeatScheduleAction(this, 0, delaySeconds, TimeUnit.SECONDS, shouldTerminate);
	}
}
