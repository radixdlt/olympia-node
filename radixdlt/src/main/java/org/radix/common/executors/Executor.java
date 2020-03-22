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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Executor
{
	private static Executor	instance = null;

	public static Executor getInstance() {
		if (instance == null) {
			int immediateThreads = Math.max(Runtime.getRuntime().availableProcessors(), 1);
			int scheduledThreads = Math.max(Runtime.getRuntime().availableProcessors() / 2, 1);
			instance = new Executor(immediateThreads, scheduledThreads);
		}

		return instance;
	}

	private final 	ExecutorService 			immediateExecutor;
	private final 	ScheduledExecutorService 	scheduledExecutor;

	public Executor(int numImmediateThreads, int numScheduledThreads)
	{
		immediateExecutor = Executors.newFixedThreadPool(numImmediateThreads);
		scheduledExecutor = Executors.newScheduledThreadPool(numScheduledThreads);
	}

	public Future<?> schedule(final ScheduledExecutable executable)
	{
		executable.setFuture(scheduledExecutor.schedule(executable, executable.getInitialDelay(), executable.getTimeUnit()));
		return executable.getFuture();
	}

	public Future<?> scheduleWithFixedDelay(final ScheduledExecutable executable)
	{
		executable.setFuture(scheduledExecutor.scheduleWithFixedDelay(executable, executable.getInitialDelay(), executable.getRecurrentDelay(), executable.getTimeUnit()));
		return executable.getFuture();
	}

	public Future<?> scheduleAtFixedRate(final ScheduledExecutable executable)
	{
		executable.setFuture(scheduledExecutor.scheduleAtFixedRate(executable, executable.getInitialDelay(), executable.getRecurrentDelay(), executable.getTimeUnit()));
		return executable.getFuture();
	}

	public Future<?> schedule(final Executable executable, int initialDelay, TimeUnit unit)
	{
		executable.setFuture(scheduledExecutor.schedule(executable, initialDelay, unit));
		return executable.getFuture();
	}

	public Future<?> submit(final Callable<?> callable)
	{
		return immediateExecutor.submit(callable);
	}

	public Future<?> submit(final Executable executable)
	{
		Future<?> future = immediateExecutor.submit(executable);
		executable.setFuture(future);
		return future;
	}
}
