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
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.Runners;
import com.radixdlt.environment.ScheduledEventProducerOnRunner;
import com.radixdlt.environment.StartProcessorOnRunner;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.ledger.LedgerUpdate;
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

	private static final Logger logger = LogManager.getLogger();

	@Override
	public void configure() {
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("TimeoutSender"));
		bind(Environment.class).to(RxEnvironment.class);
		bind(ScheduledExecutorService.class).toInstance(ses);

		// TODO: Remove, still required by SimulationNodes.java
		bind(new TypeLiteral<Observable<LedgerUpdate>>() { }).toProvider(new ObservableProvider<>(LedgerUpdate.class));
		bind(new TypeLiteral<Observable<EpochsLedgerUpdate>>() { }).toProvider(new ObservableProvider<>(EpochsLedgerUpdate.class));
		bind(new TypeLiteral<Observable<BFTHighQCUpdate>>() { }).toProvider(new ObservableProvider<>(BFTHighQCUpdate.class));

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
		@LocalEvents Set<Class<?>> localProcessedEventClasses // TODO: remove, infer from ProcessorOnRunners
	) {
		return new RxEnvironment(
			Set.of(new TypeLiteral<Epoched<ScheduledLocalTimeout>>() { }),
			localProcessedEventClasses,
			ses,
			dispatchers
		);
	}

	@ProvidesIntoMap
	@StringMapKey(Runners.CONSENSUS)
	@Singleton
	public ModuleRunner consensusRunner(
		@Self BFTNode self,
		Set<EventProcessorOnRunner<?>> processors,
		RxEnvironment rxEnvironment,
		Set<RemoteEventProcessorOnRunner<?>> remoteProcessors,
		RxRemoteEnvironment rxRemoteEnvironment,
		Set<ScheduledEventProducerOnRunner<?>> scheduledEventProducers,
		Set<StartProcessorOnRunner> startProcessors
	) {
		final var runnerName = Runners.CONSENSUS;
		final var builder = ModuleRunnerImpl.builder();
		addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
		addRemoteProcessorsOnRunner(remoteProcessors, rxRemoteEnvironment, runnerName, builder);
		addScheduledEventProducersOnRunner(scheduledEventProducers, runnerName, builder);
		addStartProcessorsOnRunner(startProcessors, runnerName, builder);
		return builder.build("ConsensusRunner " + self);
	}

	@ProvidesIntoMap
	@StringMapKey(Runners.SYSTEM_INFO)
	@Singleton
	public ModuleRunner systemInfoRunner(
		@Self BFTNode self,
		Set<EventProcessorOnRunner<?>> processors,
		RxEnvironment rxEnvironment
	) {
		final var runnerName = Runners.SYSTEM_INFO;
		final var builder = ModuleRunnerImpl.builder();
		addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
		return builder.build("SystemInfo " + self);
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

	@ProvidesIntoMap
	@StringMapKey(Runners.P2P_NETWORK)
	@Singleton
	public ModuleRunner p2pNetworkRunner(
		@Self BFTNode self,
		Set<EventProcessorOnRunner<?>> processors,
		RxEnvironment rxEnvironment,
		Set<RemoteEventProcessorOnRunner<?>> remoteProcessors,
		RxRemoteEnvironment rxRemoteEnvironment,
		Set<ScheduledEventProducerOnRunner<?>> scheduledEventProducers
	) {
		final var runnerName = Runners.P2P_NETWORK;
		final var builder = ModuleRunnerImpl.builder();
		addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
		addRemoteProcessorsOnRunner(remoteProcessors, rxRemoteEnvironment, runnerName, builder);
		addScheduledEventProducersOnRunner(scheduledEventProducers, runnerName, builder);
		return builder.build("P2PNetworkRunner " + self);
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
		TypeLiteral<T> typeLiteral,
		RxEnvironment rxEnvironment,
		EventProcessorOnRunner<?> processor,
		ModuleRunnerImpl.Builder builder
	) {
		if (processor.getRateLimitDelayMs() > 0) {
			final Flowable<T> events = rxEnvironment.getObservable(typeLiteral)
				.toFlowable(BackpressureStrategy.DROP)
				.onBackpressureBuffer(100, null, BackpressureOverflowStrategy.DROP_LATEST)
				.concatMap(e -> Flowable.timer(processor.getRateLimitDelayMs(), TimeUnit.MILLISECONDS).map(l -> e));
			processor.getProcessor(typeLiteral).ifPresent(p -> builder.add(events, p));
		} else {
			final Observable<T> events = rxEnvironment.getObservable(typeLiteral);
			processor.getProcessor(typeLiteral).ifPresent(p -> builder.add(events, p));
		}
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

	private void addStartProcessorsOnRunner(
		Set<StartProcessorOnRunner> allStartProcessors,
		String runnerName,
		ModuleRunnerImpl.Builder builder
	) {
		allStartProcessors.stream()
			.filter(p -> p.getRunnerName().equals(runnerName))
			.map(StartProcessorOnRunner::getProcessor)
			.forEach(builder::add);
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
			allRemoteProcessors
				.stream()
				.filter(p -> p.getRunnerName().equals(runnerName))
				.forEach(p -> addToBuilder(eventClass, rxRemoteEnvironment, p, builder))
		);
	}

	private void addProcessorsOnRunner(
		Set<EventProcessorOnRunner<?>> allProcessors,
		RxEnvironment rxEnvironment,
		String runnerName,
		ModuleRunnerImpl.Builder builder
	) {
		final var runnerProcessors = allProcessors.stream()
			.filter(p -> p.getRunnerName().equals(runnerName))
			.collect(Collectors.toSet());

		final var eventClasses = runnerProcessors.stream()
			.filter(e -> e.getEventClass().isPresent())
			.map(e -> e.getEventClass().get())
			.collect(Collectors.toSet());
		eventClasses.forEach(eventClass ->
			runnerProcessors.forEach(p -> addToBuilder(eventClass, rxEnvironment, p, builder))
		);

		final var typeLiterals = runnerProcessors.stream()
			.filter(e -> e.getTypeLiteral().isPresent())
			.map(e -> e.getTypeLiteral().get())
			.collect(Collectors.toSet());
		typeLiterals.forEach(typeLiteral ->
			runnerProcessors.forEach(p -> addToBuilder(typeLiteral, rxEnvironment, p, builder))
		);
	}

}
