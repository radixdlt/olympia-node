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
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.transport.SendResult;
import com.radixdlt.network.transport.Transport;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.network.transport.TransportOutboundConnection;
import com.radixdlt.serialization.Serialization;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.radix.network.messages.TestMessage;
import org.radix.network.messaging.Message;
import org.radix.serialization.RadixTest;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;

import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MessageDispatcherTest extends RadixTest {

	private MessageDispatcher messageDispatcher;
	private TransportManager transportManager;
	private PeerWithSystem peer1;
	private SystemCounters counters;
	private final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	@Before
	public void setup() {
		when(getNtpService().getUTCTimeMS()).thenAnswer((Answer<Long>) invocation -> System.currentTimeMillis());
		Serialization serialization = DefaultSerialization.getInstance();
		MessageCentralConfiguration conf = new MessagingDummyConfigurations.DummyMessageCentralConfiguration();

		RadixSystem system1 = makeSystem(EUID.ONE);
		peer1 = spy(new PeerWithSystem(system1));

		counters = mock(SystemCounters.class);
		messageDispatcher = new MessageDispatcher(
			counters, conf, serialization, () -> 30_000, hasher, getKeyPair()::sign
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

		assertThat(sendResult.getThrowable().getMessage()).isEqualTo("TTL for TestMessage message to " + peer1 + " has expired");
		verify(counters, times(1)).increment(CounterType.MESSAGES_OUTBOUND_ABORTED);
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
