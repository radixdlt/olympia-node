package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.TempoAction;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A special action which will schedule the underlying action to be dispatched after some time
 */
public class ScheduleAction implements TempoAction {
	private final TempoAction action;
	private final long delay;
	private final TimeUnit unit;

	public ScheduleAction(TempoAction action, long delay, TimeUnit unit) {
		this.action = Objects.requireNonNull(action, "action is required");
		this.delay = delay;
		this.unit = Objects.requireNonNull(unit, "unit is required");
	}

	public TempoAction getAction() {
		return action;
	}

	public long getDelay() {
		return delay;
	}

	public TimeUnit getUnit() {
		return unit;
	}
}
