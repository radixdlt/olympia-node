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

package com.radixdlt.network.messaging;

import java.util.Objects;
import java.util.function.Consumer;
import org.apache.logging.log4j.Logger;

/**
 * Simple thread pool that copies values of a specified type from a source {@link
 * InterruptibleSupplier} to a {@link Consumer} with the specified number of threads.
 *
 * @param <T> The type this class will be handling
 */
public class SimpleThreadPool<T> {
  private final Logger log;

  private final String name;
  private final InterruptibleSupplier<T> source;
  private final Consumer<T> sink;
  private final Thread[] threads;
  private volatile boolean running = false;

  /**
   * Constructs a {@code SimpleThreadPool} with the specified name, number of threads, source,
   * destination and log.
   *
   * <p>After construction has complete, the thread pool is in a dormant, ready-to-run state. Call
   * {@link #start()} to start processing objects.
   *
   * @param name The name of the thread pool
   * @param numThreads The number of threads in the thread pool
   * @param source The source of objects
   * @param sink The consumer of objects
   * @param log Where log messages should be output
   */
  public SimpleThreadPool(
      String name, int numThreads, InterruptibleSupplier<T> source, Consumer<T> sink, Logger log) {
    this.log = Objects.requireNonNull(log);
    this.name = Objects.requireNonNull(name);
    this.source = Objects.requireNonNull(source);
    this.sink = Objects.requireNonNull(sink);
    this.threads = new Thread[numThreads];
  }

  /**
   * Starts a dormant thread pool running. Note that {@link #start()} and {@link #stop()} may be
   * called multiple times in order to start and stop processing at will.
   */
  public void start() {
    synchronized (this.threads) {
      stop();
      if (!this.running) {
        this.running = true;
        for (int i = 0; i < this.threads.length; ++i) {
          this.threads[i] = new Thread(this::process, this.name + "-" + (i + 1));
          this.threads[i].setDaemon(true);
          this.threads[i].start();
        }
      }
    }
  }

  /**
   * Stops a running thread pool, returning it to the dormant state. Note that {@link #start()} and
   * {@link #stop()} may be called multiple times in order to start and stop processing at will.
   */
  public void stop() {
    synchronized (this.threads) {
      if (this.running) {
        this.running = false;
        // Interrupt all first, and then join
        for (int i = 0; i < this.threads.length; ++i) {
          if (this.threads[i] != null) {
            this.threads[i].interrupt();
          }
        }
        for (int i = 0; i < this.threads.length; ++i) {
          if (this.threads[i] != null) {
            try {
              this.threads[i].join();
            } catch (InterruptedException e) {
              log.error(this.threads[i].getName() + " did not exit before interrupt");
              // Other threads will not be joined either, as this will re-interrupt
              Thread.currentThread().interrupt();
            }
            this.threads[i] = null;
          }
        }
      }
    }
  }

  private void process() {
    while (this.running) {
      try {
        sink.accept(source.get());
      } catch (InterruptedException e) {
        // Exit
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        // Don't want to exit this loop if other exception occurs
        log.error("While processing events", e);
      }
    }
  }

  @Override
  public String toString() {
    return String.format("%s[%s]", getClass().getSimpleName(), name);
  }
}
