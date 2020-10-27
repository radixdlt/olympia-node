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

import com.radixdlt.DefaultSerialization;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.transport.SendResult;
import com.radixdlt.network.transport.Transport;
import com.radixdlt.network.transport.TransportOutboundConnection;
import com.radixdlt.serialization.Serialization;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.radix.Radix;
import org.radix.network.messages.TestMessage;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.SignedMessage;
import org.radix.serialization.RadixTest;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MessageDispatcherTest extends RadixTest {

	static class DummySignedMessage extends SignedMessage {
		DummySignedMessage() {
			super(0);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			DummySignedMessage that = (DummySignedMessage) o;
			return Objects.equals(getTimestamp(), that.getTimestamp())
					&& Objects.equals(getMagic(), that.getMagic())
					&& Objects.equals(getSignature(), that.getSignature());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getTimestamp(), getMagic(), getSignature());
		}
	}

	static class DummyMessage extends Message {
		DummyMessage() {
			super(0);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			DummyMessage that = (DummyMessage) o;
			return Objects.equals(getTimestamp(), that.getTimestamp())
					&& Objects.equals(getMagic(), that.getMagic());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getTimestamp(), getMagic());
		}
	}

	private MessageDispatcher messageDispatcher;
	private TransportManager transportManager;
	private AddressBook addressBook;
	private Peer peer1;
	private Peer peer2;
	private SystemCounters counters;
	private final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	@Before
	public void setup() {
		when(getNtpService().getUTCTimeMS()).thenAnswer((Answer<Long>) invocation -> System.currentTimeMillis());
		Serialization serialization = DefaultSerialization.getInstance();
		MessageCentralConfiguration conf = new MessagingDummyConfigurations.DummyMessageCentralConfiguration();

		peer1 = spy(new PeerWithSystem(getLocalSystem()));
		peer2 = spy(new PeerWithSystem(getLocalSystem()));

		addressBook = mock(AddressBook.class);
		when(addressBook.updatePeerSystem(peer1, peer1.getSystem())).thenReturn(peer1);
		when(addressBook.updatePeerSystem(peer2, peer2.getSystem())).thenReturn(peer2);

		counters = mock(SystemCounters.class);
		messageDispatcher = new MessageDispatcher(
			counters, conf, serialization, () -> 30_000, getLocalSystem(), addressBook, hasher, getKeyPair()::sign
		);

		// Suppression safe here - dummy outbound connection does not need closing
		@SuppressWarnings("resource")
		TransportOutboundConnection transportOutboundConnection = new MessagingDummyConfigurations.DummyTransportOutboundConnection();
		// Suppression safe here - dummy transport does not need closing
		@SuppressWarnings("resource")
		Transport transport = new MessagingDummyConfigurations.DummyTransport(transportOutboundConnection);
		transportManager = new MessagingDummyConfigurations.DummyTransportManager(transport);
	}

	@Test
	public void sendSuccessfullyMessage() throws InterruptedException, ExecutionException {
		SystemMessage message = spy(new SystemMessage(getLocalSystem(), 0));
		message.setSignature(getKeyPair().sign(hasher.hash(message)));

		MessageEvent messageEvent = new MessageEvent(peer1, message, 10_000);

		SendResult sendResult = messageDispatcher.send(transportManager, messageEvent).get();

		assertTrue(sendResult.isComplete());
	}

	@Test
	public void sendExpiredMessage() throws InterruptedException, ExecutionException {
		Message message = spy(new TestMessage(0));
		when(message.getTimestamp()).thenReturn(10_000L);
		MessageEvent messageEvent = new MessageEvent(peer1, message, 10_000);

		SendResult sendResult = messageDispatcher.send(transportManager, messageEvent).get();

		assertThat(sendResult.getThrowable().getMessage(), Matchers.equalTo("TTL for TestMessage message to " + peer1 + " has expired"));
		verify(counters, times(1)).increment(CounterType.MESSAGES_OUTBOUND_ABORTED);
	}

	@Test
	public void receiveSuccessfully() throws InterruptedException {
		SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
		testMessage.setSignature(getKeyPair().sign(hasher.hash(testMessage)));

		RadixSystem radixSystem = spy(testMessage.getSystem());
		doReturn(radixSystem).when(testMessage).getSystem();
		doReturn(EUID.ONE).when(radixSystem).getNID();

		MessageEvent messageEvent = new MessageEvent(peer1, testMessage, 10_000);

		Semaphore receivedFlag = new Semaphore(0);
		List<Message> messages = new ArrayList<>();
		MessageListenerList messageListenerList = new MessageListenerList();
		messageListenerList.addMessageListener((source, message) -> {
			messages.add(message);
			receivedFlag.release();
		});

		messageDispatcher.receive(messageListenerList, messageEvent);

		assertTrue(receivedFlag.tryAcquire(10, TimeUnit.SECONDS));
		assertThat(messages.get(0), Matchers.equalTo(testMessage));
	}

	@Test
	public void receiveExpiredMessage() {
		SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
		testMessage.setSignature(getKeyPair().sign(hasher.hash(testMessage)));

		when(testMessage.getTimestamp()).thenReturn(10_000L);
		MessageEvent messageEvent = new MessageEvent(peer1, testMessage, 10_000);

		messageDispatcher.receive(null, messageEvent);

		// execution is terminated before message.getSystem() method
		verify(testMessage, times(0)).getSystem();
		verify(counters, times(1)).increment(CounterType.MESSAGES_INBOUND_DISCARDED);
	}

	@Test
	public void receiveMessageFromBannedPeer() {
		// Banned for a long time
		peer1.setBan("Test", System.currentTimeMillis() + 86_400_000L);
		when(this.addressBook.peer(any(EUID.class))).thenReturn(Optional.of(peer1));
		RadixSystem system = mock(RadixSystem.class);
		doReturn(EUID.TWO).when(system).getNID();
		doReturn(Radix.AGENT_VERSION).when(system).getAgentVersion();
		SystemMessage testMessage = spy(new SystemMessage(system, 0));
		MessageEvent messageEvent = new MessageEvent(peer1, testMessage, 10_000);

		messageDispatcher.receive(null, messageEvent);

		// Received message not counted as discarded or processed
		verify(counters, times(1)).increment(CounterType.MESSAGES_INBOUND_RECEIVED);
		verify(counters, never()).increment(CounterType.MESSAGES_INBOUND_DISCARDED);
		verify(counters, never()).increment(CounterType.MESSAGES_INBOUND_PROCESSED);
	}

	@Test
	public void receiveDisconnectNullZeroSystem() {
		SystemMessage testMessage1 = spy(new SystemMessage(getLocalSystem(), 0));
		testMessage1.setSignature(getKeyPair().sign(hasher.hash(testMessage1)));

		RadixSystem radixSystem1 = spy(testMessage1.getSystem());
		doReturn(radixSystem1).when(testMessage1).getSystem();
		doReturn(EUID.ZERO).when(radixSystem1).getNID();
		MessageEvent messageEvent1 = new MessageEvent(peer1, testMessage1, 10_000);

		SystemMessage testMessage2 = spy(new SystemMessage(getLocalSystem(), 0));
		testMessage2.setSignature(getKeyPair().sign(hasher.hash(testMessage2)));

		RadixSystem radixSystem2 = spy(testMessage2.getSystem());
		doReturn(radixSystem2).when(testMessage2).getSystem();
		doReturn(null).when(radixSystem2).getNID();
		MessageEvent messageEvent2 = new MessageEvent(peer2, testMessage2, 10_000);

		messageDispatcher.receive(null, messageEvent1);
		messageDispatcher.receive(null, messageEvent2);

		String banMessage = "%s:SystemMessage gave null NID";
		String msg1 = String.format(banMessage, peer1);
		String msg2 = String.format(banMessage, peer2);
		verify(peer1, times(1)).ban(msg1);
		verify(peer2, times(1)).ban(msg2);
	}

	@Test
	public void receiveDisconnectOldPeer() {
		SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
		testMessage.setSignature(getKeyPair().sign(hasher.hash(testMessage)));

		RadixSystem radixSystem = spy(testMessage.getSystem());
		doReturn(radixSystem).when(testMessage).getSystem();
		doReturn(EUID.ONE).when(radixSystem).getNID();
		doReturn(Radix.REFUSE_AGENT_VERSION).when(radixSystem).getAgentVersion();
		MessageEvent messageEvent = new MessageEvent(peer1, testMessage, 10_000);

		messageDispatcher.receive(null, messageEvent);

		String banMessage = "Old peer " + peer1 + " /Radix:/2710000:100";
		verify(peer1, times(1)).ban(banMessage);
	}

	@Test
	public void receiveSelf() {
		SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
		testMessage.setSignature(getKeyPair().sign(hasher.hash(testMessage)));

		RadixSystem radixSystem = spy(testMessage.getSystem());
		doReturn(radixSystem).when(testMessage).getSystem();
		doReturn(getLocalSystem().getNID()).when(radixSystem).getNID();
		MessageEvent messageEvent = new MessageEvent(peer1, testMessage, 10_000);
		MessageListenerList listeners = mock(MessageListenerList.class);

		messageDispatcher.receive(listeners, messageEvent);

		verify(listeners, never()).messageReceived(any(), any());
	}

	@Test
	public void receiveSystemMessageBadSignature() {
		SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
		RadixSystem radixSystem = spy(testMessage.getSystem());
		doReturn(radixSystem).when(testMessage).getSystem();
		doReturn(getLocalSystem().getNID()).when(radixSystem).getNID();
		MessageEvent messageEvent = new MessageEvent(peer1, testMessage, 10_000);
		MessageListenerList listeners = mock(MessageListenerList.class);

		messageDispatcher.receive(listeners, messageEvent);

		verify(this.counters, times(1)).increment(CounterType.MESSAGES_INBOUND_BADSIGNATURE);
		verify(listeners, never()).messageReceived(any(), any());
	}

	@Test
	public void receiveSignedMessageGoodSignature() {
		SignedMessage testMessage = spy(new DummySignedMessage());
		testMessage.setSignature(getKeyPair().sign(hasher.hash(testMessage)));

		MessageEvent messageEvent = new MessageEvent(peer1, testMessage, 10_000);
		MessageListenerList listeners = mock(MessageListenerList.class);

		messageDispatcher.receive(listeners, messageEvent);

		verify(listeners, times(1)).messageReceived(any(), any());
	}

	@Test
	public void receiveSignedMessageBadSignature() {
		SignedMessage testMessage = spy(new DummySignedMessage());
		ECKeyPair bogusKey = ECKeyPair.generateNew();
		testMessage.setSignature(bogusKey.sign(hasher.hash(testMessage)));

		MessageEvent messageEvent = new MessageEvent(peer1, testMessage, 10_000);
		MessageListenerList listeners = mock(MessageListenerList.class);

		messageDispatcher.receive(listeners, messageEvent);

		verify(this.counters, times(1)).increment(CounterType.MESSAGES_INBOUND_BADSIGNATURE);
		verify(listeners, never()).messageReceived(any(), any());
	}

	@Test
	public void receiveSignedMessageNoPeer() {
		Peer noSystemPeer = mock(Peer.class);
		doReturn(false).when(noSystemPeer).hasSystem();
		SignedMessage testMessage = new DummySignedMessage();
		testMessage.setSignature(getKeyPair().sign(hasher.hash(testMessage)));

		MessageEvent messageEvent = new MessageEvent(noSystemPeer, testMessage, 10_000);
		MessageListenerList listeners = mock(MessageListenerList.class);

		messageDispatcher.receive(listeners, messageEvent);

		verify(listeners, never()).messageReceived(any(), any());
	}

	@Test
	public void receiveUnsignedMessage() {
		Message testMessage = spy(new DummyMessage());
		MessageEvent messageEvent = new MessageEvent(peer1, testMessage, 10_000);
		MessageListenerList listeners = mock(MessageListenerList.class);

		messageDispatcher.receive(listeners, messageEvent);

		verify(listeners, times(1)).messageReceived(any(), any());
	}
}
