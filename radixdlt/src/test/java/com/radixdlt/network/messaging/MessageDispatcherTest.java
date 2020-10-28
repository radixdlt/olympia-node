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

import com.google.common.collect.ImmutableMap;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.transport.SendResult;
import com.radixdlt.network.transport.Transport;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.TransportMetadata;
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
import java.util.stream.Stream;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
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
	private PeerWithSystem peer1;
	private PeerWithSystem peer2;
	private TransportInfo transportInfo1;
	private TransportInfo transportInfo2;
	private SystemCounters counters;
	private final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	@Before
	public void setup() {
		when(getNtpService().getUTCTimeMS()).thenAnswer((Answer<Long>) invocation -> System.currentTimeMillis());
		Serialization serialization = DefaultSerialization.getInstance();
		MessageCentralConfiguration conf = new MessagingDummyConfigurations.DummyMessageCentralConfiguration();

		RadixSystem system1 = makeSystem(EUID.ONE);
		RadixSystem system2 = makeSystem(EUID.TWO);
		peer1 = spy(new PeerWithSystem(system1));
		peer2 = spy(new PeerWithSystem(system2));

		transportInfo1 = peer1.supportedTransports().findFirst().get();
		transportInfo2 = peer2.supportedTransports().findFirst().get();

		addressBook = mock(AddressBook.class);
		when(addressBook.updatePeerSystem(any(), eq(system1), any())).thenReturn(peer1);
		when(addressBook.updatePeerSystem(any(), eq(system2), any())).thenReturn(peer2);
		when(addressBook.peer(eq(transportInfo1))).thenReturn(Optional.of(peer1));
		when(addressBook.peer(eq(transportInfo2))).thenReturn(Optional.of(peer2));

		counters = mock(SystemCounters.class);
		messageDispatcher = new MessageDispatcher(counters, conf, serialization, () -> 30_000, getLocalSystem(), addressBook, hasher);

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
		message.sign(getLocalSystem().getKeyPair(), hasher);

		OutboundMessageEvent messageEvent = new OutboundMessageEvent(peer1, message, 10_000);

		SendResult sendResult = messageDispatcher.send(transportManager, messageEvent).get();

		assertTrue(sendResult.isComplete());
	}

	@Test
	public void sendExpiredMessage() throws InterruptedException, ExecutionException {
		Message message = spy(new TestMessage(0));
		when(message.getTimestamp()).thenReturn(10_000L);
		OutboundMessageEvent messageEvent = new OutboundMessageEvent(peer1, message, 10_000);

		SendResult sendResult = messageDispatcher.send(transportManager, messageEvent).get();

		assertThat(sendResult.getThrowable().getMessage(), Matchers.equalTo("TTL for TestMessage message to " + peer1 + " has expired"));
		verify(counters, times(1)).increment(CounterType.MESSAGES_OUTBOUND_ABORTED);
	}

	@Test
	public void receiveSuccessfully() throws InterruptedException {
		SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
		testMessage.sign(getLocalSystem().getKeyPair(), hasher);

		RadixSystem radixSystem = spy(testMessage.getSystem());
		doReturn(radixSystem).when(testMessage).getSystem();
		doReturn(EUID.ONE).when(radixSystem).getNID();

		InboundMessageEvent messageEvent = new InboundMessageEvent(transportInfo1, testMessage, 10_000);

		Semaphore receivedFlag = new Semaphore(0);
		List<Message> messages = new ArrayList<>();
		MessageListenerList messageListenerList = new MessageListenerList();
		messageListenerList.addMessageListener((source, message) -> {
			messages.add(message);
			receivedFlag.release();
		});

		when(this.addressBook.updatePeerSystem(any(), any(), any())).thenReturn(peer1);

		messageDispatcher.receive(messageListenerList, messageEvent);

		assertTrue(receivedFlag.tryAcquire(10, TimeUnit.SECONDS));
		assertThat(messages.get(0), Matchers.equalTo(testMessage));
	}

	@Test
	public void receiveExpiredMessage() {
		SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
		testMessage.sign(getLocalSystem().getKeyPair(), hasher);

		when(testMessage.getTimestamp()).thenReturn(10_000L);
		InboundMessageEvent messageEvent = new InboundMessageEvent(transportInfo1, testMessage, 10_000);

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
		when(this.addressBook.peer(any(TransportInfo.class))).thenReturn(Optional.of(peer1));
		RadixSystem system = mock(RadixSystem.class);
		doReturn(EUID.TWO).when(system).getNID();
		doReturn(Radix.AGENT_VERSION).when(system).getAgentVersion();
		Message testMessage = new DummyMessage();
		InboundMessageEvent messageEvent = new InboundMessageEvent(transportInfo1, testMessage, 10_000);

		messageDispatcher.receive(null, messageEvent);

		// Received message not counted as processed
		verify(counters, times(1)).increment(CounterType.MESSAGES_INBOUND_RECEIVED);
		verify(counters, times(1)).increment(CounterType.MESSAGES_INBOUND_DISCARDED);
		verify(counters, never()).increment(CounterType.MESSAGES_INBOUND_PROCESSED);
	}

	@Test
	public void receiveDisconnectNullZeroSystem() {
		RadixSystem localSystem = getLocalSystem();
		SystemMessage testMessage1 = spy(new SystemMessage(localSystem, 0));
		testMessage1.sign(getLocalSystem().getKeyPair(), hasher);

		when(addressBook.updatePeerSystem(any(), eq(localSystem), any())).thenReturn(peer1);

		RadixSystem radixSystem1 = spy(testMessage1.getSystem());
		doReturn(radixSystem1).when(testMessage1).getSystem();
		doReturn(EUID.ZERO).when(radixSystem1).getNID();
		InboundMessageEvent messageEvent1 = new InboundMessageEvent(transportInfo1, testMessage1, 10_000);

		SystemMessage testMessage2 = spy(new SystemMessage(localSystem, 0));
		testMessage2.sign(getLocalSystem().getKeyPair(), hasher);

		RadixSystem radixSystem2 = spy(testMessage2.getSystem());
		doReturn(radixSystem2).when(testMessage2).getSystem();
		doReturn(null).when(radixSystem2).getNID();
		InboundMessageEvent messageEvent2 = new InboundMessageEvent(transportInfo2, testMessage2, 10_000);

		when(addressBook.updatePeerSystem(any(), eq(radixSystem1), any())).thenReturn(peer1);
		when(addressBook.updatePeerSystem(any(), eq(radixSystem2), any())).thenReturn(peer2);

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
		testMessage.sign(getLocalSystem().getKeyPair(), hasher);

		RadixSystem radixSystem = spy(testMessage.getSystem());
		doReturn(radixSystem).when(testMessage).getSystem();
		doReturn(EUID.ONE).when(radixSystem).getNID();
		doReturn(Radix.REFUSE_AGENT_VERSION).when(radixSystem).getAgentVersion();
		InboundMessageEvent messageEvent = new InboundMessageEvent(transportInfo1, testMessage, 10_000);

		when(this.addressBook.updatePeerSystem(any(), any(), any())).thenReturn(peer1);

		messageDispatcher.receive(null, messageEvent);

		String banMessage = "Old peer " + peer1 + " /Radix:/2710000:100";
		verify(peer1, times(1)).ban(banMessage);
	}

	@Test
	public void receiveSelf() {
		SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
		testMessage.sign(getLocalSystem().getKeyPair(), hasher);

		RadixSystem radixSystem = spy(testMessage.getSystem());
		doReturn(radixSystem).when(testMessage).getSystem();
		doReturn(getLocalSystem().getNID()).when(radixSystem).getNID();
		InboundMessageEvent messageEvent = new InboundMessageEvent(transportInfo1, testMessage, 10_000);
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
		InboundMessageEvent messageEvent = new InboundMessageEvent(transportInfo1, testMessage, 10_000);
		MessageListenerList listeners = mock(MessageListenerList.class);

		messageDispatcher.receive(listeners, messageEvent);

		verify(this.counters, times(1)).increment(CounterType.MESSAGES_INBOUND_BADSIGNATURE);
		verify(listeners, never()).messageReceived(any(), any());
	}

	@Test
	public void receiveSignedMessageGoodSignature() {
		SignedMessage testMessage = spy(new DummySignedMessage());
		testMessage.sign(getLocalSystem().getKeyPair(), hasher);

		InboundMessageEvent messageEvent = new InboundMessageEvent(transportInfo1, testMessage, 10_000);
		MessageListenerList listeners = mock(MessageListenerList.class);

		messageDispatcher.receive(listeners, messageEvent);

		verify(listeners, times(1)).messageReceived(any(), any());
	}

	@Test
	public void receiveSignedMessageBadSignature() {
		SignedMessage testMessage = spy(new DummySignedMessage());
		ECKeyPair bogusKey = ECKeyPair.generateNew();
		testMessage.sign(bogusKey, hasher);

		InboundMessageEvent messageEvent = new InboundMessageEvent(transportInfo1, testMessage, 10_000);
		MessageListenerList listeners = mock(MessageListenerList.class);

		messageDispatcher.receive(listeners, messageEvent);

		verify(this.counters, times(1)).increment(CounterType.MESSAGES_INBOUND_BADSIGNATURE);
		verify(listeners, never()).messageReceived(any(), any());
	}

	@Test
	public void receiveSignedMessageNoPeer() {
		SignedMessage testMessage = new DummySignedMessage();
		testMessage.sign(getLocalSystem().getKeyPair(), hasher);

		InboundMessageEvent messageEvent = new InboundMessageEvent(mock(TransportInfo.class), testMessage, 10_000);
		MessageListenerList listeners = mock(MessageListenerList.class);

		messageDispatcher.receive(listeners, messageEvent);

		verify(listeners, never()).messageReceived(any(), any());
	}

	@Test
	public void receiveUnsignedMessage() {
		Message testMessage = spy(new DummyMessage());
		InboundMessageEvent messageEvent = new InboundMessageEvent(transportInfo1, testMessage, 10_000);
		MessageListenerList listeners = mock(MessageListenerList.class);

		messageDispatcher.receive(listeners, messageEvent);

		verify(listeners, times(1)).messageReceived(any(), any());
	}


	private RadixSystem makeSystem(EUID nid) {
		RadixSystem system = mock(RadixSystem.class);
		TransportInfo ti = TransportInfo.of("TCP", TransportMetadata.create(
			ImmutableMap.of("nid", nid.toString())));

		when(system.getNID()).thenReturn(nid);
		when(system.supportedTransports()).thenAnswer(inv -> Stream.of(ti));
		when(system.getKey()).thenReturn(getLocalSystem().getKey());

		return system;
	}
}
