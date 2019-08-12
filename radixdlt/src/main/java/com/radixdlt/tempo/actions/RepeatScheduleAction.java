package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.TempoAction;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A special action which will schedule the underlying action to be dispatched after some time
 */
public class RepeatScheduleAction implements TempoAction {
	private final TempoAction action;
	private final long initialDelay;
	private final long recurrentDelay;
	private final TimeUnit unit;

	public RepeatScheduleAction(TempoAction action, long initialDelay, long recurrentDelay, TimeUnit unit) {
		this.action = Objects.requireNonNull(action, "action is required");
		this.initialDelay = initialDelay;
		this.recurrentDelay = recurrentDelay;
		this.unit = Objects.requireNonNull(unit, "unit is required");
	}

	public TempoAction getAction() {
		return action;
	}

	public long getInitialDelay() {
		return initialDelay;
	}

	public long getRecurrentDelay() {
		return recurrentDelay;
	}

	public TimeUnit getUnit() {
		return unit;
	}
}
