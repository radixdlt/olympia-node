/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.environment.rx;

import com.google.common.collect.ImmutableList;
import com.radixdlt.ModuleRunner;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.StartProcessor;
import com.radixdlt.utils.ThreadFactories;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Executes chaos related events
 */
public final class ModuleRunnerImpl implements ModuleRunner {
	private static final Logger logger = LogManager.getLogger();
	private final Scheduler singleThreadScheduler;
	private final ScheduledExecutorService executorService;
	private final String threadName;
	private final Object startLock = new Object();
	private CompositeDisposable compositeDisposable;

	private final Set<StartProcessor> startProcessors;
	private final List<Subscription<?>> subscriptions;
	private final ImmutableList<Consumer<ScheduledExecutorService>> onStart;

	private static class Subscription<T> {
		final Observable<T> o;
		final EventProcessor<T> p;

		Subscription(Observable<T> o, EventProcessor<T> p) {
			this.o = o;
			this.p = p;
		}

		Disposable subscribe(Scheduler s) {
			return o.observeOn(s).subscribe(p::process, e -> {
				// TODO: Implement better error handling especially against Byzantine nodes.
				// TODO: Exit process for now.
				e.printStackTrace();
				Thread.sleep(1000);
				System.exit(-1);
			});
		}
	}

	private ModuleRunnerImpl(
		String threadName,
		Set<StartProcessor> startProcessors, // TODO: combine with onStart
		List<Subscription<?>> subscriptions,
		ImmutableList<Consumer<ScheduledExecutorService>> onStart
	) {
		this.threadName = threadName;
		this.startProcessors = startProcessors;
		this.subscriptions = subscriptions;
		this.executorService = 	Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads(threadName));
		this.singleThreadScheduler = Schedulers.from(this.executorService);
		this.onStart = onStart;
	}

	public static class Builder {
		private HashSet<StartProcessor> startProcessors = new HashSet<>();
		private ImmutableList.Builder<Subscription<?>> subscriptionsBuilder = ImmutableList.builder();
		private ImmutableList.Builder<Consumer<ScheduledExecutorService>> onStartBuilder = new ImmutableList.Builder<>();

		public Builder add(StartProcessor startProcessor) {
			startProcessors.add(startProcessor);
			return this;
		}

		public <T> Builder add(Observable<T> o, EventProcessor<T> p) {
			subscriptionsBuilder.add(new Subscription<>(o, p));
			return this;
		}

		public <T> Builder add(Flowable<T> o, EventProcessor<T> p) {
			subscriptionsBuilder.add(new Subscription<>(o.toObservable(), p));
			return this;
		}

		public <T> Builder add(Flowable<RemoteEvent<T>> o, RemoteEventProcessor<T> p) {
			subscriptionsBuilder.add(new Subscription<>(o.toObservable(), p::process));
			return this;
		}

		public <T> Builder scheduleWithFixedDelay(
			EventDispatcher<T> eventDispatcher,
			Supplier<T> eventSupplier,
			Duration initialDelay,
			Duration interval
		) {
			return onStart(executor ->
				executor.scheduleWithFixedDelay(
					() -> eventDispatcher.dispatch(eventSupplier.get()),
					initialDelay.toMillis(),
					interval.toMillis(),
					TimeUnit.MILLISECONDS
				)
			);
		}

		public Builder onStart(Consumer<ScheduledExecutorService> fn) {
			this.onStartBuilder.add(fn);
			return this;
		}

		public ModuleRunnerImpl build(String threadName) {
			return new ModuleRunnerImpl(
				threadName,
				Set.copyOf(startProcessors),
				subscriptionsBuilder.build(),
				onStartBuilder.build()
			);
		}
	}

	public static Builder builder() {
		return new Builder();
	}


	@Override
	public void start() {
		synchronized (this.startLock) {
			if (this.compositeDisposable != null) {
				return;
			}

			logger.info("Starting Runner: {}", this.threadName);

			this.executorService.submit(() -> startProcessors.forEach(StartProcessor::start));
			final var disposables = this.subscriptions.stream()
				.map(s -> s.subscribe(singleThreadScheduler))
				.collect(Collectors.toList());
			this.compositeDisposable = new CompositeDisposable(disposables);


			this.onStart.forEach(f -> f.accept(this.executorService));
		}
	}

	@Override
	public void stop() {
		synchronized (this.startLock) {
			if (compositeDisposable != null) {
				compositeDisposable.dispose();
				compositeDisposable = null;

				this.shutdownAndAwaitTermination();
			}
		}
	}

	private void shutdownAndAwaitTermination() {
		this.executorService.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!this.executorService.awaitTermination(2, TimeUnit.SECONDS)) {
				this.executorService.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!this.executorService.awaitTermination(2, TimeUnit.SECONDS)) {
					System.err.println("Pool " + this.threadName + " did not terminate");
				}
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			this.executorService.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}
}
