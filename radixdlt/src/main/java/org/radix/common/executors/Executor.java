package org.radix.common.executors;

import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Executor
{
	private static final Logger log = Logging.getLogger();

	private static Executor	instance = null;

	public static Executor getInstance() {
		if (instance == null) {
			// Ideally, this should be read from a configuration file. However, Executor is currently used
			// from within LocalSystem, which is a frequently accessed global mess and does not allow me
			// to easily pass in the configuration. As a lesser evil, this will remain static for now.
			instance = new Executor(1, 1);
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
