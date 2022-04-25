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

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import com.google.common.collect.ImmutableList;
import com.radixdlt.modules.ModuleRunner;
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
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ModuleRunnerImpl implements ModuleRunner {
  private static final Logger logger = LogManager.getLogger();
  private ScheduledExecutorService executorService;
  private final String threadName;
  private final Object startLock = new Object();
  private CompositeDisposable compositeDisposable;

  private final Set<StartProcessor> startProcessors;
  private final List<Subscription<?>> subscriptions;
  private final ImmutableList<Consumer<ScheduledExecutorService>> onStart;

  private record Subscription<T>(Observable<T> o, EventProcessor<T> p) {
    Disposable subscribe(Scheduler s) {
      return o.observeOn(s)
          .subscribe(
              p::process,
              e -> {
                // TODO: Implement better error handling especially against Byzantine nodes.
                // TODO: Exit process for now.
                logger.error(
                    "Unhandled exception in the event processing loop. Shutting down the node. ",
                    e);
                Thread.sleep(1000);
                System.exit(-1);
              });
    }
  }

  private ModuleRunnerImpl(
      String threadName,
      Set<StartProcessor> startProcessors, // TODO: combine with onStart
      List<Subscription<?>> subscriptions,
      ImmutableList<Consumer<ScheduledExecutorService>> onStart) {
    this.threadName = threadName;
    this.startProcessors = startProcessors;
    this.subscriptions = subscriptions;
    this.onStart = onStart;
  }

  public static class Builder {
    private final HashSet<StartProcessor> startProcessors = new HashSet<>();
    private final ImmutableList.Builder<Subscription<?>> subscriptionsBuilder =
        ImmutableList.builder();
    private final ImmutableList.Builder<Consumer<ScheduledExecutorService>> onStartBuilder =
        new ImmutableList.Builder<>();

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
        Duration interval) {
      return onStart(
          executor ->
              executor.scheduleWithFixedDelay(
                  () -> eventDispatcher.dispatch(eventSupplier.get()),
                  initialDelay.toMillis(),
                  interval.toMillis(),
                  TimeUnit.MILLISECONDS));
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
          onStartBuilder.build());
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

      this.executorService =
          newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads(threadName));
      var singleThreadScheduler = Schedulers.from(this.executorService);

      logger.info("Starting Runner: {}", this.threadName);

      this.executorService.submit(() -> startProcessors.forEach(StartProcessor::start));
      final var disposables =
          this.subscriptions.stream().map(s -> s.subscribe(singleThreadScheduler)).toList();
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
      if (!this.executorService.awaitTermination(500, TimeUnit.MILLISECONDS)) {
        this.executorService.shutdownNow(); // Cancel currently executing tasks
        // Wait a while for tasks to respond to being cancelled
        if (!this.executorService.awaitTermination(500, TimeUnit.MILLISECONDS)) {
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
