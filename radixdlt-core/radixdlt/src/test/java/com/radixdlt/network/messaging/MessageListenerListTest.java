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

import org.junit.Before;
import org.junit.Test;
import org.radix.network.messages.TestMessage;

import com.radixdlt.network.addressbook.Peer;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertNull;
import static org.assertj.core.api.Assertions.assertThat;

// Unit tests for MessageListenerList
public class MessageListenerListTest {

	// Internal variables.
	private MessageListenerList listenerList;
	int called;

	// Setup for all tests. Constructs and creates a model, and initialises local counter
	@Before
	public void setUp() {
		listenerList = new MessageListenerList();
		called = 0;
	}

	@Test
	public void testCallListener() {
		MessageListener<TestMessage> listener = (peer, message) -> this.called += 1;

		listenerList.addMessageListener(listener);
		// Should not have been called yet
		assertThat(called).isZero();

		listenerList.messageReceived(null, null);
		// Should have been called because of messageReceived()
		assertThat(called).isEqualTo(1);

		listenerList.messageReceived(null, null);
		// Should have been called again because of messageReceived
		assertThat(called).isEqualTo(2);

		listenerList.removeMessageListener(listener);
		listenerList.messageReceived(null, null);
		// Should not have been called -> removed
		assertThat(called).isEqualTo(2);
	}

	@Test
	public void testRemoveAll() {
		MessageListener<TestMessage> listener = (peer, message) -> this.called += 1;

		listenerList.addMessageListener(listener);
		// Should not have been called yet
		assertThat(called).isZero();

		listenerList.messageReceived(null, null);
		// Should have been called because of messageReceived()
		assertThat(called).isEqualTo(1);

		listenerList.removeAllMessageListeners();
		listenerList.messageReceived(null, null);
		// Should not have been called -> removed
		assertThat(called).isEqualTo(1);
	}

	private final MessageListener<TestMessage> testListener = new MessageListener<>() {
		@Override
		public void handleMessage(Peer source, TestMessage message) {
			called += 1;
			listenerList.removeMessageListener(testListener);
		}
	};

	// Test that we can remove callbacks in a callback without deadlocking
	@Test
	public void testRemoveOwnCallback() {
		assertThat(called).isZero(); // precondition
		listenerList.addMessageListener(testListener);
		assertThat(called).isZero(); // nothing happened
		listenerList.messageReceived(null, null);
		assertThat(called).isEqualTo(1); // called and removed itself
		listenerList.messageReceived(null, null);
		assertThat(called).isEqualTo(1); // was removed, so didn't happen again
	}

	// Test that we can remove a callback for another thread in this thread w/o deadlock
	@Test
	public void testRemoveOthersCallback() throws Exception {
		final Lock lock = new ReentrantLock(true);
		final Condition cond = lock.newCondition();
		final MessageListener<TestMessage> listener1 = (peer, message) -> {
			if (message == null) {
				lock.lock();
				cond.signal();
				lock.unlock();
				try {
					Thread.sleep(200);
				} catch (Exception e) {
					assertNull(e);
				}
				called += 1;
			}
		};
		MessageListener<TestMessage> listener2 = (peer, message) -> {
			if (message != null) {
				cond.awaitUninterruptibly();
				lock.unlock();
				assertThat(called).isZero();
				listenerList.removeMessageListener(listener1);
				assertThat(called).isEqualTo(1);
			}
		};
		listenerList.addMessageListener(listener1);
		listenerList.addMessageListener(listener2);
		lock.lock();
		Thread otherThread = new Thread(() -> listenerList.messageReceived(null, null));
		otherThread.start();
		listenerList.messageReceived(null, new TestMessage(1));
		otherThread.join();
	}

	// Test that adding null listener throws
	@Test(expected = IllegalArgumentException.class)
	public void testAddNullListener() {
		listenerList.addMessageListener(null);
	}

	// Test that removing null listener throws
	@Test(expected = IllegalArgumentException.class)
	public void testRemoveNullListener() {
		listenerList.removeMessageListener(null);
	}

	// Test that adding listener twice throws
	@Test(expected = IllegalArgumentException.class)
	public void testAddListenerTwice() {
		MessageListener<TestMessage> listener = (peer, message) -> { };
		listenerList.addMessageListener(listener);
		listenerList.addMessageListener(listener);
	}
}