/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.api.store.berkeley;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The purpose of this data structure is to collect elements (using {@link #push(Object)} method) and then
 * process collected elements at once using {@link #consumeCollected(Consumer)} method. While processing takes place
 * new elements can be stored (they will be processed during next call to {@link #consumeCollected(Consumer)}).
 */
public final class StackingCollector<T> {
	private final AtomicReference<Node<T>> head = new AtomicReference<>();

	private StackingCollector() { }

	public static <T> StackingCollector<T> create() {
		return new StackingCollector<>();
	}

	/**
	 * Add element to collector.
	 *
	 * @param element element to add.
	 */
	public void push(final T element) {
		final var newHead = new Node<>(element);
		Node<T> oldHead;

		do {
			oldHead = head.get();
			newHead.nextNode = oldHead;
		} while (!head.compareAndSet(oldHead, newHead));
	}

	/**
	 * Remove all collected elements and pass them to provided {@link Consumer} one by one.
	 * This method guarantees that each element will be submitted to provided {@link Consumer} only once.
	 *
	 * @param consumer element consumer
	 *
	 * @return number of processed elements.
	 */
	public int consumeCollected(final Consumer<T> consumer) {
		//Note: this is very performance critical method, so all internals are inlined
		Node<T> head;

		//Detach stored data from head
		do {
			head = this.head.get();
		} while (!this.head.compareAndSet(head, null));

		//Reverse list
		Node<T> current = head;
		Node<T> prev = null;
		Node<T> next;

		while (current != null) {
			next = current.nextNode;
			current.nextNode = prev;
			prev = current;
			current = next;
		}

		int count = 0;
		//Process elements
		while (prev != null) {
			consumer.accept(prev.element);
			prev = prev.nextNode;
			count++;
		}
		return count;
	}

	// CHECKSTYLE:OFF
	// For performance reasons this class is not private and its fields are public.
	static final class Node<T> {
		public T element;
		public Node<T> nextNode;

		Node(final T element) {
			this.element = element;
		}
	}
	// CHECKSTYLE:ON
}