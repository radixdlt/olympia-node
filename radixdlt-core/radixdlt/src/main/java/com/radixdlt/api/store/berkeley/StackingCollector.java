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