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

package com.radixdlt.network.messaging;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of a {@link SimpleBlockingQueue} that provides
 * priority ordering, while respecting FIFO properties
 * within a priority class.
 *
 * @param <T> The element type
 */
class SimplePriorityBlockingQueue<T> implements SimpleBlockingQueue<T> {
	private final PriorityBlockingQueue<SimpleEntry<T>> queue;

	static final class SimpleEntry<U> {
		private static final AtomicLong sequence = new AtomicLong(0);

		final long seq;
		final U entry;

		SimpleEntry(U entry) {
			this.seq = sequence.getAndIncrement();
			this.entry = entry;
		}

		long getSeq() {
			return this.seq;
		}

		U getEntry() {
			return this.entry;
		}

		@Override
		public String toString() {
			return this.entry.toString();
		}
	}

	SimplePriorityBlockingQueue(int size, Comparator<? super T> comparator) {
		this.queue = new PriorityBlockingQueue<>(size, comparator(comparator));
	}

	private Comparator<SimpleEntry<T>> comparator(Comparator<? super T> comparator) {
		return Comparator.comparing(SimpleEntry<T>::getEntry, comparator).thenComparingLong(SimpleEntry<T>::getSeq);
	}

	@Override
	public T take() throws InterruptedException {
		return this.queue.take().getEntry();
	}

	@Override
	public boolean offer(T item) {
		return this.queue.offer(new SimpleEntry<>(Objects.requireNonNull(item)));
	}

	@Override
	public int size() {
		return this.queue.size();
	}

	// No straightforward way to implement equals, so not doing that here

	@Override
	public String toString() {
		return this.queue.toString();
	}
}
