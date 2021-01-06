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

package org.radix.utils;

import java.util.Objects;
import java.util.function.Consumer;

import org.apache.logging.log4j.Logger;

/**
 * Simple thread pool that copies values of a specified type from a source
 * {@link InterruptibleSupplier} to a {@link Consumer} with the specified
 * number of threads.
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
	 * Constructs a {@code SimpleThreadPool} with the specified name, number
	 * of threads, source, destination and log.
	 * <p>
	 * After construction has complete, the thread pool is in a dormant,
	 * ready-to-run state.  Call {@link #start()} to start processing objects.
	 *
	 * @param name The name of the thread pool
	 * @param numThreads The number of threads in the thread pool
	 * @param source The source of objects
	 * @param sink The consumer of objects
	 * @param log Where log messages should be output
	 */
	public SimpleThreadPool(String name, int numThreads, InterruptibleSupplier<T> source, Consumer<T> sink, Logger log) {
		this.log = Objects.requireNonNull(log);
		this.name = Objects.requireNonNull(name);
		this.source = Objects.requireNonNull(source);
		this.sink = Objects.requireNonNull(sink);
		this.threads = new Thread[numThreads];
	}

	/**
	 * Starts a dormant thread pool running.
	 * Note that {@link #start()} and {@link #stop()} may be called
	 * multiple times in order to start and stop processing at will.
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
	 * Stops a running thread pool, returning it to the dormant state.
	 * Note that {@link #start()} and {@link #stop()} may be called
	 * multiple times in order to start and stop processing at will.
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
