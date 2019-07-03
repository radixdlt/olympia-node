package org.radix.time;

import java.util.concurrent.atomic.AtomicLong;

public final class LogicalClock
{
	private static LogicalClock instance = null;

	public static synchronized LogicalClock getInstance()
	{
		if (instance == null)
			instance = new LogicalClock();

		return instance;
	}

	private final AtomicLong clock;

	public LogicalClock()
	{
		clock = new AtomicLong(0);
	}

	public LogicalClock(long value)
	{
		clock = new AtomicLong(value);
	}

	public long get()
	{
		return clock.get();
	}

	public long incrementAndGet()
	{
		return clock.incrementAndGet();
	}

	public void set(long value)
	{
		clock.set(value);
	}
}
