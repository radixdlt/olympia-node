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

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.radix.network.messaging.Message;

/**
 * Fairly straightforward implementation of a multithreaded queue. The only
 * non-trivial thing is the helper method
 * {@link #waitCallbackComplete(MessageListener)} to wait for the current
 * callback to complete.
 * <p>
 * Oh, also.  Umm.  This is externally locked.  Sorry.
 */
class MessageListenerQueue {
	private final LinkedList<MessageListener<Message>> theQueue = new LinkedList<>();
	private final Lock lock = new ReentrantLock();
	private final Condition popped = lock.newCondition();
	private long processingThread;

	MessageListenerQueue(Collection<MessageListener<Message>> listeners) {
		processingThread = Thread.currentThread().getId();
		theQueue.addAll(listeners);
	}

	boolean remove(MessageListener<?> w) {
		return theQueue.remove(w);
	}

	boolean pop() {
		theQueue.removeFirst();
		popped.signal();
		return !theQueue.isEmpty();
	}

	boolean isEmpty() {
		return theQueue.isEmpty();
	}

	MessageListener<Message> head() {
		return isEmpty() ? null : theQueue.getFirst();
	}

	MessageListener<Message> nextHead() {
		if (theQueue.isEmpty()) {
			return null;
		}
		pop();
		return head();
	}

	Lock getLock() {
		return lock;
	}

	/**
	 * Return {@code true} if we are the thread that is processing the queue.
	 * This is used to ensure that we don't block ourselves when waiting for
	 * callbacks to complete.
	 *
	 * @return {@code true} if we are the thread processing the callback queue,
	 *         {@code false} otherwise.
	 */
	boolean isProcessingThread() {
		return processingThread == Thread.currentThread().getId();
	}

	/**
	 * Wait until {@code listener} is not at the head of the queue. This should be
	 * called with {@code lock} held, and is performed by waiting on
	 * {@code cond}, which is signalled whenever {@link #pop()} is called.
	 *
	 * @param listener The listener callback to wait on completion for.
	 */
	void waitCallbackComplete(MessageListener<?> listener) {
		while (!isEmpty() && listener.equals(head())) {
			popped.awaitUninterruptibly();
		}
	}

}