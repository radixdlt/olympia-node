package org.radix.network2.messaging;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.radix.modules.Modules;
import org.radix.network.messages.TestMessage;
import org.radix.network2.addressbook.Peer;

import com.radixdlt.universe.Universe;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.CoreMatchers.*;

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

		// Curse you singletons
		Universe universe = mock(Universe.class);
		when(universe.getMagic()).thenReturn(0);

		Modules.put(Universe.class, universe);
	}

	@After
	public void cleanup() {
		Modules.remove(Universe.class);
	}

	@Test
	public void testCallListener() {
		MessageListener<TestMessage> listener = (peer, message) -> this.called += 1;

		listenerList.addMessageListener(listener);
		// Should not have been called yet
		assertThat(called, equalTo(0));

		listenerList.messageReceived(null, null);
		// Should have been called because of messageReceived()
		assertThat(called, equalTo(1));

		listenerList.messageReceived(null, null);
		// Should have been called again because of messageReceived
		assertThat(called, equalTo(2));

		listenerList.removeMessageListener(listener);
		listenerList.messageReceived(null, null);
		// Should not have been called -> removed
		assertThat(called, equalTo(2));
	}

	@Test
	public void testRemoveAll() {
		MessageListener<TestMessage> listener = (peer, message) -> this.called += 1;

		listenerList.addMessageListener(listener);
		// Should not have been called yet
		assertThat(called, equalTo(0));

		listenerList.messageReceived(null, null);
		// Should have been called because of messageReceived()
		assertThat(called, equalTo(1));

		listenerList.removeAllMessageListeners();
		listenerList.messageReceived(null, null);
		// Should not have been called -> removed
		assertThat(called, equalTo(1));
	}

	private final MessageListener<TestMessage> testListener = new MessageListener<TestMessage>() {
		@Override
		public void handleMessage(Peer source, TestMessage message) {
			called += 1;
			listenerList.removeMessageListener(testListener);
		}
	};

	// Test that we can remove callbacks in a callback without deadlocking
	@Test
	public void testRemoveOwnCallback() {
		assertThat(called, equalTo(0)); // precondition
		listenerList.addMessageListener(testListener);
		assertThat(called, equalTo(0)); // nothing happened
		listenerList.messageReceived(null, null);
		assertThat(called, equalTo(1)); // called and removed itself
		listenerList.messageReceived(null, null);
		assertThat(called, equalTo(1)); // was removed, so didn't happen again
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
				assertThat(called, equalTo(0));
				listenerList.removeMessageListener(listener1);
				assertThat(called, equalTo(1));
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
		MessageListener<TestMessage> listener = (peer, message) -> {};
		listenerList.addMessageListener(listener);
		listenerList.addMessageListener(listener);
	}
}