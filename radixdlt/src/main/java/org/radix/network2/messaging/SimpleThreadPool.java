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
