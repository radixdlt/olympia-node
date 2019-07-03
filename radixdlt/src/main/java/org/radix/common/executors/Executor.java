package org.radix.common.executors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;

public class Executor
{
	private static final Logger log = Logging.getLogger();

	private static Executor	instance = null;

	public static Executor getInstance()
	{
		if (instance == null)
			instance = new Executor();

		return instance;
	}

	private final 	ExecutorService 			immediateExecutor;
	private final 	ScheduledExecutorService 	scheduledExecutor;

	private Executor()
	{
		immediateExecutor = Executors.newFixedThreadPool(Math.max(Modules.get(RuntimeProperties.class).get("jobs.threads.immediate", Runtime.getRuntime().availableProcessors()), 1));
		scheduledExecutor = Executors.newScheduledThreadPool(Math.max(Modules.get(RuntimeProperties.class).get("jobs.threads.scheduled", Runtime.getRuntime().availableProcessors()/2), 1));
	}

	public Executor(int numImmediateThreads, int numScheduledThreads)
	{
		immediateExecutor = Executors.newFixedThreadPool(numImmediateThreads);
		scheduledExecutor = Executors.newScheduledThreadPool(numScheduledThreads);
	}

	public Executor(int numImmediateThreads, ThreadFactory immediateThreadFactory, int numScheduledThreads, ThreadFactory scheduledThreadFactory)
	{
		immediateExecutor = Executors.newFixedThreadPool(numImmediateThreads, immediateThreadFactory);
		scheduledExecutor = Executors.newScheduledThreadPool(numScheduledThreads, scheduledThreadFactory);
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
