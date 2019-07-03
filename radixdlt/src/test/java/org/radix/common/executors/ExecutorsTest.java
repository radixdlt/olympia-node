package org.radix.common.executors;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExecutorsTest
{
	private final Executor executor = new Executor(1, 1);

	@Test
	public void executableDelayTest() throws InterruptedException
	{
		Semaphore latch = new Semaphore(0);

		Executable executable = new Executable()
		{
			@Override
			public void execute()
			{
				latch.release();
			}
		};

		executor.schedule(executable, 250, TimeUnit.MILLISECONDS);

		Thread.sleep(500);

		Assert.assertEquals(1, latch.availablePermits());
	}

	@Test
	public void executableDelayCancelTest() throws InterruptedException
	{
		Semaphore latch = new Semaphore(0);

		Executable executable = new Executable()
		{
			@Override
			public void execute()
			{
				latch.release();
			}
		};

		executor.schedule(executable, 250, TimeUnit.MILLISECONDS);

		Thread.sleep(100);
		executable.terminate(false);
		Thread.sleep(250);

		Assert.assertEquals(0, latch.availablePermits());
	}

	@Test
	public void scheduledExecutableFixedRateCancelTest() throws InterruptedException
	{
		Semaphore latch = new Semaphore(0);

		ScheduledExecutable executable = new ScheduledExecutable(100, 100, TimeUnit.MILLISECONDS)
		{
			@Override
			public void execute()
			{
				latch.release();
			}
		};

		executor.scheduleAtFixedRate(executable);

		Thread.sleep(450);
		executable.terminate(false);
		Thread.sleep(100);

		Assert.assertEquals(4, latch.availablePermits());
	}

	@Test
	public void scheduledExecutableFixedDelayCancelTest() throws InterruptedException
	{
		Semaphore latch = new Semaphore(0);

		ScheduledExecutable executable = new ScheduledExecutable(100, 100, TimeUnit.MILLISECONDS)
		{
			@Override
			public void execute()
			{
				latch.release();
			}
		};

		executor.scheduleWithFixedDelay(executable);

		Thread.sleep(450);
		executable.terminate(false);
		Thread.sleep(100);

		Assert.assertEquals(4, latch.availablePermits());
	}

	@Before
	public void clearInterruptedFlag() {
		// Clear interrupted flag
		Thread.interrupted();
	}
}
