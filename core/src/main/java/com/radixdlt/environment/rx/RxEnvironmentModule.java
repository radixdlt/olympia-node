/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.environment.rx;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.modules.ModuleRunner;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.Runners;
import com.radixdlt.environment.ScheduledEventProducerOnRunner;
import com.radixdlt.environment.StartProcessorOnRunner;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.utils.ThreadFactories;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Environment utilizing RxJava */
public final class RxEnvironmentModule extends AbstractModule {
  @Override
  public void configure() {
    ScheduledExecutorService ses =
        Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("TimeoutSender"));
    bind(Environment.class).to(RxEnvironment.class);
    bind(ScheduledExecutorService.class).toInstance(ses);

    // TODO: Remove, still required by SimulationNodes.java
    bind(new TypeLiteral<Observable<LedgerUpdate>>() {})
        .toProvider(new ObservableProvider<>(LedgerUpdate.class));
    bind(new TypeLiteral<Observable<BFTHighQCUpdate>>() {})
        .toProvider(new ObservableProvider<>(BFTHighQCUpdate.class));

    Multibinder.newSetBinder(binder(), new TypeLiteral<RxRemoteDispatcher<?>>() {});
    Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessorOnRunner<?>>() {});
    Multibinder.newSetBinder(binder(), new TypeLiteral<RemoteEventProcessorOnRunner<?>>() {});
    Multibinder.newSetBinder(binder(), new TypeLiteral<ScheduledEventProducerOnRunner<?>>() {});
  }

  @Provides
  @Singleton
  private RxEnvironment rxEnvironment(
      ScheduledExecutorService ses,
      Set<RxRemoteDispatcher<?>> dispatchers,
      @LocalEvents
          Set<Class<?>> localProcessedEventClasses // TODO: remove, infer from ProcessorOnRunners
      ) {
    return new RxEnvironment(
        Set.of(new TypeLiteral<Epoched<ScheduledLocalTimeout>>() {}),
        localProcessedEventClasses,
        ses,
        dispatchers);
  }

  @ProvidesIntoMap
  @StringMapKey(Runners.CONSENSUS)
  @Singleton
  public ModuleRunner consensusRunner(
      @Self String name,
      Set<EventProcessorOnRunner<?>> processors,
      RxEnvironment rxEnvironment,
      Set<RemoteEventProcessorOnRunner<?>> remoteProcessors,
      RxRemoteEnvironment rxRemoteEnvironment,
      Set<ScheduledEventProducerOnRunner<?>> scheduledEventProducers,
      Set<StartProcessorOnRunner> startProcessors) {
    final var runnerName = Runners.CONSENSUS;
    final var builder = ModuleRunnerImpl.builder();
    addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
    addRemoteProcessorsOnRunner(remoteProcessors, rxRemoteEnvironment, runnerName, builder);
    addScheduledEventProducersOnRunner(scheduledEventProducers, runnerName, builder);
    addStartProcessorsOnRunner(startProcessors, runnerName, builder);
    return builder.build("BFT " + name);
  }

  @ProvidesIntoMap
  @StringMapKey(Runners.SYSTEM_INFO)
  @Singleton
  public ModuleRunner systemInfoRunner(
      @Self String name, Set<EventProcessorOnRunner<?>> processors, RxEnvironment rxEnvironment) {
    final var runnerName = Runners.SYSTEM_INFO;
    final var builder = ModuleRunnerImpl.builder();
    addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
    return builder.build("SystemInfo " + name);
  }

  @ProvidesIntoMap
  @StringMapKey(Runners.MEMPOOL)
  @Singleton
  public ModuleRunner mempoolRunner(
      @Self String name,
      Set<EventProcessorOnRunner<?>> processors,
      RxEnvironment rxEnvironment,
      Set<RemoteEventProcessorOnRunner<?>> remoteProcessors,
      RxRemoteEnvironment rxRemoteEnvironment,
      Set<ScheduledEventProducerOnRunner<?>> scheduledEventProducers) {
    final var runnerName = Runners.MEMPOOL;
    final var builder = ModuleRunnerImpl.builder();
    addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
    addRemoteProcessorsOnRunner(remoteProcessors, rxRemoteEnvironment, runnerName, builder);
    addScheduledEventProducersOnRunner(scheduledEventProducers, runnerName, builder);
    return builder.build("MempoolRunner " + name);
  }

  @ProvidesIntoMap
  @StringMapKey(Runners.SYNC)
  @Singleton
  public ModuleRunner syncRunner(
      @Self String name,
      Set<EventProcessorOnRunner<?>> processors,
      RxEnvironment rxEnvironment,
      Set<RemoteEventProcessorOnRunner<?>> remoteProcessors,
      RxRemoteEnvironment rxRemoteEnvironment,
      Set<ScheduledEventProducerOnRunner<?>> scheduledEventProducers) {

    final var runnerName = Runners.SYNC;
    final var builder = ModuleRunnerImpl.builder();
    addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
    addRemoteProcessorsOnRunner(remoteProcessors, rxRemoteEnvironment, runnerName, builder);
    addScheduledEventProducersOnRunner(scheduledEventProducers, runnerName, builder);
    return builder.build("SyncRunner " + name);
  }

  @ProvidesIntoMap
  @StringMapKey(Runners.P2P_NETWORK)
  @Singleton
  public ModuleRunner p2pNetworkRunner(
      @Self String name,
      Set<EventProcessorOnRunner<?>> processors,
      RxEnvironment rxEnvironment,
      Set<RemoteEventProcessorOnRunner<?>> remoteProcessors,
      RxRemoteEnvironment rxRemoteEnvironment,
      Set<ScheduledEventProducerOnRunner<?>> scheduledEventProducers) {
    final var runnerName = Runners.P2P_NETWORK;
    final var builder = ModuleRunnerImpl.builder();
    addProcessorsOnRunner(processors, rxEnvironment, runnerName, builder);
    addRemoteProcessorsOnRunner(remoteProcessors, rxRemoteEnvironment, runnerName, builder);
    addScheduledEventProducersOnRunner(scheduledEventProducers, runnerName, builder);
    return builder.build("P2PNetworkRunner " + name);
  }

  private static <T> void addToBuilder(
      Class<T> eventClass,
      RxRemoteEnvironment rxEnvironment,
      RemoteEventProcessorOnRunner<?> processor,
      ModuleRunnerImpl.Builder builder) {
    final Flowable<RemoteEvent<T>> events;
    if (processor.getRateLimitDelayMs() > 0) {
      events =
          rxEnvironment
              .remoteEvents(eventClass)
              .onBackpressureBuffer(100, null, BackpressureOverflowStrategy.DROP_LATEST)
              .concatMap(
                  e ->
                      Flowable.timer(processor.getRateLimitDelayMs(), TimeUnit.MILLISECONDS)
                          .map(l -> e));
    } else {
      events = rxEnvironment.remoteEvents(eventClass);
    }

    processor.getProcessor(eventClass).ifPresent(p -> builder.add(events, p));
  }

  private static <T> void addToBuilder(
      TypeLiteral<T> typeLiteral,
      RxEnvironment rxEnvironment,
      EventProcessorOnRunner<?> processor,
      ModuleRunnerImpl.Builder builder) {
    if (processor.getRateLimitDelayMs() > 0) {
      final Flowable<T> events =
          rxEnvironment
              .getObservable(typeLiteral)
              .toFlowable(BackpressureStrategy.DROP)
              .onBackpressureBuffer(100, null, BackpressureOverflowStrategy.DROP_LATEST)
              .concatMap(
                  e ->
                      Flowable.timer(processor.getRateLimitDelayMs(), TimeUnit.MILLISECONDS)
                          .map(l -> e));
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
      ModuleRunnerImpl.Builder builder) {
    if (processor.getRateLimitDelayMs() > 0) {
      final Flowable<T> events =
          rxEnvironment
              .getObservable(eventClass)
              .toFlowable(BackpressureStrategy.DROP)
              .onBackpressureBuffer(100, null, BackpressureOverflowStrategy.DROP_LATEST)
              .concatMap(
                  e ->
                      Flowable.timer(processor.getRateLimitDelayMs(), TimeUnit.MILLISECONDS)
                          .map(l -> e));
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
      ModuleRunnerImpl.Builder builder) {
    allScheduledEventProducers.stream()
        .filter(p -> p.runnerName().equals(runnerName))
        .forEach(
            scheduledEventProducer ->
                builder.scheduleWithFixedDelay(
                    (EventDispatcher<Object>) scheduledEventProducer.eventDispatcher(),
                    (Supplier<Object>) scheduledEventProducer.eventSupplier(),
                    scheduledEventProducer.initialDelay(),
                    scheduledEventProducer.interval()));
  }

  private void addStartProcessorsOnRunner(
      Set<StartProcessorOnRunner> allStartProcessors,
      String runnerName,
      ModuleRunnerImpl.Builder builder) {
    allStartProcessors.stream()
        .filter(p -> p.runnerName().equals(runnerName))
        .map(StartProcessorOnRunner::processor)
        .forEach(builder::add);
  }

  private void addRemoteProcessorsOnRunner(
      Set<RemoteEventProcessorOnRunner<?>> allRemoteProcessors,
      RxRemoteEnvironment rxRemoteEnvironment,
      String runnerName,
      ModuleRunnerImpl.Builder builder) {
    final var remoteEventClasses =
        allRemoteProcessors.stream()
            .filter(p -> p.getRunnerName().equals(runnerName))
            .map(RemoteEventProcessorOnRunner::getEventClass)
            .collect(Collectors.toSet());
    remoteEventClasses.forEach(
        eventClass ->
            allRemoteProcessors.stream()
                .filter(p -> p.getRunnerName().equals(runnerName))
                .forEach(p -> addToBuilder(eventClass, rxRemoteEnvironment, p, builder)));
  }

  private void addProcessorsOnRunner(
      Set<EventProcessorOnRunner<?>> allProcessors,
      RxEnvironment rxEnvironment,
      String runnerName,
      ModuleRunnerImpl.Builder builder) {
    final var runnerProcessors =
        allProcessors.stream()
            .filter(p -> p.getRunnerName().equals(runnerName))
            .collect(Collectors.toSet());

    final var eventClasses =
        runnerProcessors.stream()
            .filter(e -> e.getEventClass().isPresent())
            .map(e -> e.getEventClass().get())
            .collect(Collectors.toSet());
    eventClasses.forEach(
        eventClass ->
            runnerProcessors.forEach(p -> addToBuilder(eventClass, rxEnvironment, p, builder)));

    final var typeLiterals =
        runnerProcessors.stream()
            .filter(e -> e.getTypeLiteral().isPresent())
            .map(e -> e.getTypeLiteral().get())
            .collect(Collectors.toSet());
    typeLiterals.forEach(
        typeLiteral ->
            runnerProcessors.forEach(p -> addToBuilder(typeLiteral, rxEnvironment, p, builder)));
  }
}
