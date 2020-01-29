/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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
