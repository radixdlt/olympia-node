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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;
import org.radix.utils.Locking;

import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.network.addressbook.Peer;

/**
 * The {@code MessageListenerList} class is a thread-safe list of listeners.
 * <p>
 * In particular, this call ensures that callbacks will not be currently executing
 * in another thread, nor will they be scheduled to run on threads in the future
 * once the {@link #removeMessageListener(MessageListener)} or {@link #removeAllMessageListeners()}
 * methods have returned.
 */
final class MessageListenerList {
	private static final Logger log = LogManager.getLogger();

	/**
	 * A list of queues of callbacks that we are currently processing.
	 */
	private final List<MessageListenerQueue> processingQueues = new LinkedList<>();
	private final Lock processingQueuesLock = new ReentrantLock(true);

	/**
	 * A lock for {@code messageListeners}.
	 */
	private final Lock messageListenersLock = new ReentrantLock(true);

	/**
	 * The complete set of wrapped {@link MessageListener} objects for this list.
	 */
	private Set<MessageListener<Message>> messageListeners = new HashSet<>();

	/**
	 * Creates a new {@code MessageListenerList} object.
	 */
	MessageListenerList() {
		// Empty
	}

	/**
	 * Notifies all listeners that a message has been received.
	 *
	 * @param source the {@link Peer} the message was received from
	 * @param message the {@link Message} that was received
	 */
	void messageReceived(Peer source, Message message) {
		notifyListeners(source, message);
	}

	/**
	 * Adds the given {@link MessageListener} as a listener to this list. The
	 * listener will be notified when {@link #messageReceived(Peer, Message)} is called.
	 *
	 * @param listener an object implementing the {@link MessageListener} interface
	 */
	void addMessageListener(final MessageListener<? extends Message> listener) {
		if (listener == null) {
			throw new IllegalArgumentException("Cannot add a null listener");
		}
		MessageListener<Message> untypedListener = untyped(listener);
		if (!Locking.withPredicateLock(messageListenersLock, messageListeners::add, untypedListener)) {
			throw new IllegalArgumentException(String.format("Cannot add multiple listeners. Attempted to add a %s on %s.",
				listener.getClass().getSimpleName(), getClass().getSimpleName()));
		}
	}

	/**
	 * Removes the given {@link MessageListener}.
	 *
	 * @param listener the {@link MessageListener} to remove
	 */
	void removeMessageListener(final MessageListener<?> listener) {
		if (listener == null) {
			throw new IllegalArgumentException("Cannot remove a null listener");
		}

		// Remove the target so it's not being eligible to be put on new queues.
		Locking.withConsumerLock(messageListenersLock, messageListeners::remove, listener);

		// Remove target from any running queues
		Locking.withConsumerLock(processingQueuesLock, this::removeTargetFromAllQueues, listener);
	}

	/**
	 * Removes all listeners.
	 * The listener list will be empty on return if no additional threads
	 * have added listeners.
	 */
	void removeAllMessageListeners() {
		// Make a copy of the current listeners
		List<MessageListener<?>> listenersToRemove = new ArrayList<>();
		Locking.withConsumerLock(messageListenersLock, listenersToRemove::addAll, this.messageListeners);

		for (MessageListener<?> w : listenersToRemove) {
			removeMessageListener(w);
		}
	}

	@VisibleForTesting
	int size() {
		return Locking.withSupplierLock(messageListenersLock, messageListeners::size);
	}

	private void notifyListeners(Peer source, Message message) {
		// Add the listener to the processing queue.
		notifyListeners(Locking.withSupplierLock(messageListenersLock, () -> new MessageListenerQueue(messageListeners)), source, message);
	}

	private void notifyListeners(final MessageListenerQueue whom, Peer source, Message message) {
		// Add this queue to the queues we are processing
		Locking.withConsumerLock(processingQueuesLock, processingQueues::add, whom);
		try {
			for (MessageListener<Message> listener = Locking.withFunctionLock(whom.getLock(), MessageListenerQueue::head, whom);
				listener != null; listener = Locking.withFunctionLock(whom.getLock(), MessageListenerQueue::nextHead, whom)) {
				try {
					listener.handleMessage(source, message);
				} catch (Exception e) {
					log.error("While processing message callback", e);
				}
			}
		} finally {
			Locking.withConsumerLock(processingQueuesLock, processingQueues::remove, whom);
		}
	}

	private void removeTargetFromQueue(MessageListenerQueue mlq, MessageListener<?> target) {
		MessageListener<?> messageListener = mlq.head();
		// Just give up if the queue has been exhausted but not yet removed
		if (messageListener == null) {
			return;
		}

		if (messageListener.equals(target)) {
			// If we are removing a running callback, and are NOT the thread running the callback,
			// then we need to wait for the callback to complete. Otherwise we do nothing if we
			// are removing ourself.
			if (!mlq.isProcessingThread()) {
				mlq.waitCallbackComplete(messageListener);
			} else {
				// Not running now, so just remove from the queue
				mlq.remove(target);
			}
		}
	}

	private void removeTargetFromAllQueues(MessageListener<?> target) {
		for (final MessageListenerQueue mlq : processingQueues) {
			Locking.withConsumerLock(mlq.getLock(), this::removeTargetFromQueue, mlq, target);
		}
	}

	@SuppressWarnings("unchecked")
	private MessageListener<Message> untyped(MessageListener<? extends Message> listener) {
		// Type abuse
		return (MessageListener<Message>) listener;
	}
}
