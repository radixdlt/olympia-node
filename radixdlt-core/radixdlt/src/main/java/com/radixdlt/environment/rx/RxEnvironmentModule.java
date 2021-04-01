/*
 * (C) Copyright 2020 Radix DLT Ltd
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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.chaos.mempoolfiller.ScheduledMempoolFill;
import com.radixdlt.chaos.messageflooder.MessageFlooderUpdate;
import com.radixdlt.chaos.messageflooder.ScheduledMessageFlood;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.ScheduledEventProducerOnRunner;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolRelayCommands;
import com.radixdlt.mempool.MempoolRelayTrigger;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncLedgerUpdateTimeout;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.utils.ThreadFactories;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Environment utilizing RxJava
 */
public final class RxEnvironmentModule extends AbstractModule {

	public static final class Runners {
		public static final String SYNC = "sync";
		public static final String MEMPOOL = "mempool";
		public static final String APPLICATION = "application";
		public static final String CHAOS = "chaos";
		public static final String CONSENSUS = "consensus";
	}

	private static final Logger logger = LogManager.getLogger();

	@Override
	public void configure() {
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("TimeoutSender"));
		bind(Environment.class).to(RxEnvironment.class);
		bind(ScheduledExecutorService.class).toInstance(ses);

		bind(new TypeLiteral<Observable<MempoolAddFailure>>() { }).toProvider(new ObservableProvider<>(MempoolAddFailure.class));
		bind(new TypeLiteral<Observable<MempoolRelayTrigger>>() { }).toProvider(new ObservableProvider<>(MempoolRelayTrigger.class));
		bind(new TypeLiteral<Observable<MempoolRelayCommands>>() { }).toProvider(new ObservableProvider<>(MempoolRelayCommands.class));
		bind(new TypeLiteral<Observable<ScheduledLocalTimeout>>() { }).toProvider(new ObservableProvider<>(ScheduledLocalTimeout.class));
		bind(new TypeLiteral<Observable<BFTInsertUpdate>>() { }).toProvider(new ObservableProvider<>(BFTInsertUpdate.class));
		bind(new TypeLiteral<Observable<BFTRebuildUpdate>>() { }).toProvider(new ObservableProvider<>(BFTRebuildUpdate.class));
		bind(new TypeLiteral<Observable<BFTHighQCUpdate>>() { }).toProvider(new ObservableProvider<>(BFTHighQCUpdate.class));
		bind(new TypeLiteral<Observable<BFTCommittedUpdate>>() { }).toProvider(new ObservableProvider<>(BFTCommittedUpdate.class));
		bind(new TypeLiteral<Observable<VertexRequestTimeout>>() { }).toProvider(new ObservableProvider<>(VertexRequestTimeout.class));
		bind(new TypeLiteral<Observable<LocalSyncRequest>>() { }).toProvider(new ObservableProvider<>(LocalSyncRequest.class));
		bind(new TypeLiteral<Observable<SyncCheckTrigger>>() { }).toProvider(new ObservableProvider<>(SyncCheckTrigger.class));
		bind(new TypeLiteral<Observable<SyncCheckReceiveStatusTimeout>>() { })
			.toProvider(new ObservableProvider<>(SyncCheckReceiveStatusTimeout.class));
		bind(new TypeLiteral<Observable<SyncRequestTimeout>>() { }).toProvider(new ObservableProvider<>(SyncRequestTimeout.class));
		bind(new TypeLiteral<Observable<SyncLedgerUpdateTimeout>>() { }).toProvider(new ObservableProvider<>(SyncLedgerUpdateTimeout.class));
		bind(new TypeLiteral<Observable<LocalTimeoutOccurrence>>() { }).toProvider(new ObservableProvider<>(LocalTimeoutOccurrence.class));
		bind(new TypeLiteral<Observable<EpochLocalTimeoutOccurrence>>() { })
			.toProvider(new ObservableProvider<>(EpochLocalTimeoutOccurrence.class));
		bind(new TypeLiteral<Observable<EpochViewUpdate>>() { }).toProvider(new ObservableProvider<>(EpochViewUpdate.class));
		bind(new TypeLiteral<Observable<ViewUpdate>>() { }).toProvider(new ObservableProvider<>(ViewUpdate.class));
		bind(new TypeLiteral<Observable<AtomsCommittedToLedger>>() { }).toProvider(new ObservableProvider<>(AtomsCommittedToLedger.class));
		bind(new TypeLiteral<Observable<MessageFlooderUpdate>>() { }).toProvider(new ObservableProvider<>(MessageFlooderUpdate.class));
		bind(new TypeLiteral<Observable<ScheduledMessageFlood>>() { }).toProvider(new ObservableProvider<>(ScheduledMessageFlood.class));
		bind(new TypeLiteral<Observable<MempoolFillerUpdate>>() { }).toProvider(new ObservableProvider<>(MempoolFillerUpdate.class));
		bind(new TypeLiteral<Observable<ScheduledMempoolFill>>() { }).toProvider(new ObservableProvider<>(ScheduledMempoolFill.class));
		bind(new TypeLiteral<Observable<Epoched<ScheduledLocalTimeout>>>() { })
			.toProvider(new ObservableProvider<>(new TypeLiteral<Epoched<ScheduledLocalTimeout>>() { }));
		bind(new TypeLiteral<Observable<LedgerUpdate>>() { }).toProvider(new ObservableProvider<>(LedgerUpdate.class));
		bind(new TypeLiteral<Observable<EpochsLedgerUpdate>>() { }).toProvider(new ObservableProvider<>(EpochsLedgerUpdate.class));
		bind(new TypeLiteral<Observable<AtomsRemovedFromMempool>>() { }).toProvider(new ObservableProvider<>(AtomsRemovedFromMempool.class));

		bind(new TypeLiteral<Flowable<RemoteEvent<MempoolAdd>>>() { }).toProvider(new RemoteEventsProvider<>(MempoolAdd.class));

		Multibinder.newSetBinder(binder(), new TypeLiteral<RxRemoteDispatcher<?>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessorOnRunner<?>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<RemoteEventProcessorOnRunner<?>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<ScheduledEventProducerOnRunner<?>>() { });
	}

	@Provides
	@Singleton
	private RxEnvironment rxEnvironment(
		ScheduledExecutorService ses,
		Set<RxRemoteDispatcher<?>> dispatchers,
		@LocalEvents Set<Class<?>> localProcessedEventClasses
	) {
		return new RxEnvironment(
			Set.of(new TypeLiteral<Epoched<ScheduledLocalTimeout>>() { }),
			localProcessedEventClasses,
			ses,
			dispatchers
		);
	}

	@ProvidesIntoMap
	@StringMapKey(Runners.CHAOS)
	@Singleton
	public ModuleRunner chaosRunner(
		@Self BFTNode self,
		Set<EventProcessorOnRunner<?>> processors,
		RxEnvironment rxEnvironment
	) {
		final var runnerName = Runners.CHAOS;
		final var builder = ModuleRunnerImpl.builder();
		addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
		return builder.build("ChaosRunner " + self);
	}

	@ProvidesIntoMap
	@StringMapKey(Runners.MEMPOOL)
	@Singleton
	public ModuleRunner mempoolRunner(
		@Self BFTNode self,
		Set<EventProcessorOnRunner<?>> processors,
		RxEnvironment rxEnvironment,
		Set<RemoteEventProcessorOnRunner<?>> remoteProcessors,
		RxRemoteEnvironment rxRemoteEnvironment,
		Set<ScheduledEventProducerOnRunner<?>> scheduledEventProducers
	) {
		final var runnerName = Runners.MEMPOOL;
		final var builder = ModuleRunnerImpl.builder();
		addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
		addRemoteProcessorsOnRunner(remoteProcessors, rxRemoteEnvironment, runnerName, builder);
		addScheduledEventProducersOnRunner(scheduledEventProducers, runnerName, builder);
		return builder.build("MempoolRunner " + self);
	}

	@ProvidesIntoMap
	@StringMapKey(Runners.APPLICATION)
	@Singleton
	public ModuleRunner applicationRunner(
		@Self BFTNode self,
		Set<EventProcessorOnRunner<?>> processors,
		RxEnvironment rxEnvironment
	) {
		final var runnerName = Runners.APPLICATION;
		final var builder = ModuleRunnerImpl.builder();
		addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
		return builder.build("ApplicationRunner " + self);
	}

	@ProvidesIntoMap
	@StringMapKey(Runners.SYNC)
	@Singleton
	public ModuleRunner syncRunner(
		@Self BFTNode self,
		Set<EventProcessorOnRunner<?>> processors,
		RxEnvironment rxEnvironment,
		Set<RemoteEventProcessorOnRunner<?>> remoteProcessors,
		RxRemoteEnvironment rxRemoteEnvironment,
		Set<ScheduledEventProducerOnRunner<?>> scheduledEventProducers
	) {
		final var runnerName = Runners.SYNC;
		final var builder = ModuleRunnerImpl.builder();
		addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
		addRemoteProcessorsOnRunner(remoteProcessors, rxRemoteEnvironment, runnerName, builder);
		addScheduledEventProducersOnRunner(scheduledEventProducers, runnerName, builder);
		return builder.build("SyncRunner " + self);
	}

	private static <T> void addToBuilder(
		Class<T> eventClass,
		RxRemoteEnvironment rxEnvironment,
		RemoteEventProcessorOnRunner<?> processor,
		ModuleRunnerImpl.Builder builder
	) {
		final Flowable<RemoteEvent<T>> events;
		if (processor.getRateLimitDelayMs() > 0) {
			events = rxEnvironment.remoteEvents(eventClass)
				.onBackpressureBuffer(100, null, BackpressureOverflowStrategy.DROP_LATEST)
				.concatMap(e -> Flowable.timer(processor.getRateLimitDelayMs(), TimeUnit.MILLISECONDS).map(l -> e));
		} else {
			events = rxEnvironment.remoteEvents(eventClass);
		}

		processor.getProcessor(eventClass).ifPresent(p -> builder.add(events, p));
	}

	private static <T> void addToBuilder(
		Class<T> eventClass,
		RxEnvironment rxEnvironment,
		EventProcessorOnRunner<?> processor,
		ModuleRunnerImpl.Builder builder
	) {
		if (processor.getRateLimitDelayMs() > 0) {
			final Flowable<T> events = rxEnvironment.getObservable(eventClass)
				.toFlowable(BackpressureStrategy.DROP)
				.onBackpressureBuffer(100, null, BackpressureOverflowStrategy.DROP_LATEST)
				.concatMap(e -> Flowable.timer(processor.getRateLimitDelayMs(), TimeUnit.MILLISECONDS).map(l -> e));
			processor.getProcessor(eventClass).ifPresent(p -> builder.add(events, p));
		} else {
			final Observable<T> events = rxEnvironment.getObservable(eventClass);
			processor.getProcessor(eventClass).ifPresent(p -> builder.add(events, p));
		}
	}

	@SuppressWarnings("unchecked")
	private void addScheduledEventProducersOnRunner(
			Set<ScheduledEventProducerOnRunner<?>> allScheduledEventProducers,
			String runnerName,
			ModuleRunnerImpl.Builder builder
	) {
		allScheduledEventProducers.stream()
			.filter(p -> p.getRunnerName().equals(runnerName))
			.forEach(scheduledEventProducer ->
				builder.scheduleWithFixedDelay(
					(EventDispatcher<Object>) scheduledEventProducer.getEventDispatcher(),
					(Supplier<Object>) scheduledEventProducer.getEventSupplier(),
					scheduledEventProducer.getInitialDelay(),
					scheduledEventProducer.getInterval()
				)
			);
	}

	private void addRemoteProcessorsOnRunner(
		Set<RemoteEventProcessorOnRunner<?>> allRemoteProcessors,
		RxRemoteEnvironment rxRemoteEnvironment,
		String runnerName,
		ModuleRunnerImpl.Builder builder
	) {
		final var remoteEventClasses = allRemoteProcessors.stream()
			.filter(p -> p.getRunnerName().equals(runnerName))
			.map(RemoteEventProcessorOnRunner::getEventClass)
			.collect(Collectors.toSet());
		remoteEventClasses.forEach(eventClass ->
			allRemoteProcessors.forEach(p -> addToBuilder(eventClass, rxRemoteEnvironment, p, builder))
		);
	}

	private void addProcessorsOnRunner(
		Set<EventProcessorOnRunner<?>> allProcessors,
		RxEnvironment rxEnvironment,
		String runnerName,
		ModuleRunnerImpl.Builder builder
	) {
		final var eventClasses = allProcessors.stream()
			.filter(p -> p.getRunnerName().equals(runnerName))
			.map(EventProcessorOnRunner::getEventClass)
			.collect(Collectors.toSet());
		eventClasses.forEach(eventClass ->
			allProcessors.forEach(p -> addToBuilder(eventClass, rxEnvironment, p, builder))
		);
	}

}
