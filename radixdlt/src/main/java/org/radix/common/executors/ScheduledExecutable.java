package org.radix.common.executors;

import java.util.concurrent.TimeUnit;

import org.radix.logging.Logger;
import org.radix.logging.Logging;

public abstract class ScheduledExecutable extends Executable
{
	private static final Logger log = Logging.getLogger();

	private final long initialDelay;
	private final long recurrentDelay;
	private final TimeUnit unit;

	public ScheduledExecutable(long initialDelay, long recurrentDelay, TimeUnit unit)
	{
		super();

		this.initialDelay = initialDelay;
		this.recurrentDelay = recurrentDelay;
		this.unit = unit;
	}

	public long getInitialDelay() { return this.initialDelay; }

	public long getRecurrentDelay() { return this.recurrentDelay; }

	public TimeUnit getTimeUnit() { return this.unit; }
}

