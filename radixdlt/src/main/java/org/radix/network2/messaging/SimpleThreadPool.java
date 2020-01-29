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

package org.radix.network2.messaging;

import java.util.Objects;
import java.util.function.Consumer;

import org.radix.logging.Logger;
import org.radix.logging.Logging;

class SimpleThreadPool<T> {
	private static final Logger log = Logging.getLogger("messaging");

	private final String name;
	private final InterruptibleSupplier<T> source;
	private final Consumer<T> destination;
	private final Thread[] threads;

	SimpleThreadPool(String name, int numThreads, InterruptibleSupplier<T> source, Consumer<T> destination) {
		this.name = Objects.requireNonNull(name);
		this.source = Objects.requireNonNull(source);
		this.destination = Objects.requireNonNull(destination);
		this.threads = new Thread[numThreads];
	}

	void start() {
		stop();
		for (int i = 0; i < this.threads.length; ++i) {
			this.threads[i] = new Thread(this::process, this.name + "-" + (i + 1));
			this.threads[i].setDaemon(true);
			this.threads[i].start();
		}
	}

	void stop() {
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
					log.error(this.threads[i].getName() + " did not exit before interrupt", e);
				}
				this.threads[i] = null;
			}
		}
	}

	private void process() {
		for (;;) {
			try {
				destination.accept(source.get());
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
