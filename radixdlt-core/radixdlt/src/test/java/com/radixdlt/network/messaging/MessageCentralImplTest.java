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
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.messaging.MessagingDummyConfigurations.DummyTransport;
import com.radixdlt.network.messaging.MessagingDummyConfigurations.DummyTransportOutboundConnection;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Compress;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.radix.network.messages.TestMessage;
import org.radix.network.messaging.Message;
import org.radix.universe.system.LocalSystem;

import java.io.IOException;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;

public class MessageCentralImplTest {

	static class TestBlockingQueue<T> extends SimplePriorityBlockingQueue<T> {
		private final AtomicLong offered = new AtomicLong(0);
		private final AtomicBoolean full = new AtomicBoolean(false);

		TestBlockingQueue(Comparator<T> comparator) {
			super(100, comparator);
		}

		@Override
		public boolean offer(T e) {
			this.offered.incrementAndGet();
			if (this.full.get()) {
				return false;
			} else {
				return super.offer(e);
			}
		}
	}

	private Serialization serialization;
	private DummyTransportOutboundConnection toc;
	private DummyTransport dt;
	private MessageCentralImpl mci;
	private TestBlockingQueue<OutboundMessageEvent> outboundQueue;

	@Before
	public void testSetup() {
		this.serialization = DefaultSerialization.getInstance();
		MessageCentralConfiguration conf = new MessagingDummyConfigurations.DummyMessageCentralConfiguration();

		// Curse you singletons
		RuntimeProperties runtimeProperties = mock(RuntimeProperties.class);
		doReturn("").when(runtimeProperties).get(eq("network.whitelist"), any());
		AddressBook addressBook = mock(AddressBook.class);
		PeerWithSystem peer = mock(PeerWithSystem.class);
		doReturn(Optional.of(peer)).when(addressBook).peer(any(TransportInfo.class));

		// Other scaffolding
		this.toc = new DummyTransportOutboundConnection();
		this.dt = new DummyTransport(this.toc);

		// Safe, as this is a dummy transport with no underlying resources
		@SuppressWarnings("resource")
		TransportManager transportManager = new MessagingDummyConfigurations.DummyTransportManager(this.dt);

		outboundQueue = new TestBlockingQueue<>(OutboundMessageEvent.comparator());
		EventQueueFactory<OutboundMessageEvent> outboundQueueFactory = eventQueueFactoryMock();
		doReturn(outboundQueue).when(outboundQueueFactory).createEventQueue(eq(conf.messagingOutboundQueueMax(0)), any());
		LocalSystem localSystem = mock(LocalSystem.class);
		SystemCounters counters = mock(SystemCounters.class);
		ECKeyPair ecKeyPair = ECKeyPair.generateNew();
		this.mci = new MessageCentralImpl(
			new MessagingDummyConfigurations.DummyMessageCentralConfiguration(),
			serialization,
			transportManager,
			addressBook,
			System::currentTimeMillis,
			outboundQueueFactory,
			localSystem,
			counters,
			Sha256Hasher.withDefaultSerialization(),
			ecKeyPair::sign
		);
	}

	@After
	public void tearDown() {
	    mci.close();
	}

	@Test
	public void testClose() {
		mci.close();
		assertTrue(dt.isClosed());
	}

	@Test
	public void testSend() throws InterruptedException {
		Message msg = new TestMessage(1);
		Peer peer = mock(Peer.class);
		mci.send(peer, msg);
		assertTrue(toc.getCountDownLatch().await(10, TimeUnit.SECONDS));
		assertTrue(toc.isSent());
	}

	@Test
	public void testSendMessageDeliveredToTransport() throws InterruptedException {
		Message msg = new TestMessage(1);
		Peer peer = mock(Peer.class);

		int numberOfRequests = 6;
		CountDownLatch receivedFlag = new CountDownLatch(numberOfRequests);
		toc.setCountDownLatch(receivedFlag);
		for (int i = 0; i < numberOfRequests; i++) {
			mci.send(peer, msg);
		}

		assertTrue(receivedFlag.await(10, TimeUnit.SECONDS));
		assertEquals(numberOfRequests, toc.getMessages().size());
	}

	@Test
	public void testInbound() throws IOException, InterruptedException {
		Message msg = new TestMessage(1);
		byte[] data = Compress.compress(serialization.toDson(msg, Output.WIRE));

		AtomicReference<Message> receivedMessage = new AtomicReference<>();
		Semaphore receivedFlag = new Semaphore(0);

		mci.messagesOf(TestMessage.class).subscribe(message -> {
			receivedMessage.set(message.getMessage());
			receivedFlag.release();
		});

		TransportInfo source = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		InboundMessage message = InboundMessage.of(source, data);
		dt.inboundMessage(message);

		assertTrue(receivedFlag.tryAcquire(10, TimeUnit.SECONDS));
		assertNotNull(receivedMessage.get());
	}

	@SuppressWarnings("unchecked")
	private <T> EventQueueFactory<T> eventQueueFactoryMock() {
		return mock(EventQueueFactory.class);
	}
}
