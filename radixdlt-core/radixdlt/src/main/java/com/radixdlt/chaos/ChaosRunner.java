package com.radixdlt.chaos;

import com.google.inject.Inject;
import com.radixdlt.ModuleRunner;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.utils.ThreadFactories;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class ChaosRunner implements ModuleRunner {

	private final Scheduler singleThreadScheduler;
	private final ScheduledExecutorService executorService;
	private final Observable<ScheduledMessageFlood> scheduledSwarms;
	private final EventProcessor<ScheduledMessageFlood> scheduledSwarmEventProcessor;
	private final Observable<MessageFloodUpdate> messageFloodUpdates;
	private final EventProcessor<MessageFloodUpdate> messageFloodUpdateProcessor;

	private final Object startLock = new Object();
	private CompositeDisposable compositeDisposable;

	@Inject
	public ChaosRunner(
		Observable<ScheduledMessageFlood> scheduledFloods,
		EventProcessor<ScheduledMessageFlood> scheduledFloodProcessor,
		Observable<MessageFloodUpdate> messageFloodUpdates,
		EventProcessor<MessageFloodUpdate> messageFloodUpdateProcessor
	) {
		this.scheduledSwarms = Objects.requireNonNull(scheduledFloods);
		this.scheduledSwarmEventProcessor = Objects.requireNonNull(scheduledFloodProcessor);
		this.messageFloodUpdates = Objects.requireNonNull(messageFloodUpdates);
		this.messageFloodUpdateProcessor = Objects.requireNonNull(messageFloodUpdateProcessor);
		this.executorService = 	Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("ChaosRunner"));
		this.singleThreadScheduler = Schedulers.from(this.executorService);
	}

	@Override
	public void start() {
		synchronized (this.startLock) {
			if (this.compositeDisposable == null) {
			    return;
			}

			Disposable d0 = this.scheduledSwarms
				.observeOn(singleThreadScheduler)
				.subscribe(this.scheduledSwarmEventProcessor::process);

			Disposable d1 = this.messageFloodUpdates
					.observeOn(singleThreadScheduler)
					.subscribe(this.messageFloodUpdateProcessor::process);

			compositeDisposable = new CompositeDisposable(d0, d1);
		}
	}

	@Override
	public void stop() {
		synchronized (this.startLock) {
			if (compositeDisposable != null) {
				compositeDisposable.dispose();
				compositeDisposable = null;
			}
		}
	}
}
