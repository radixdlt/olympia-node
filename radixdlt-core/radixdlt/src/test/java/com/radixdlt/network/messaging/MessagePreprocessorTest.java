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
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.serialization.Serialization;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.radix.Radix;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.SignedMessage;
import org.radix.serialization.RadixTest;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MessagePreprocessorTest extends RadixTest {

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

	private MessagePreprocessor messagePreprocessor;
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
		when(addressBook.addOrUpdatePeer(any(), eq(system1), any())).thenReturn(peer1);
		when(addressBook.addOrUpdatePeer(any(), eq(system2), any())).thenReturn(peer2);
		when(addressBook.peer(transportInfo1)).thenReturn(Optional.of(peer1));
		when(addressBook.peer(transportInfo2)).thenReturn(Optional.of(peer2));

		counters = mock(SystemCounters.class);
		messagePreprocessor = new MessagePreprocessor(
			counters, conf, () -> 30_000, getLocalSystem(), addressBook, hasher, serialization
		);
	}

	@Test
	public void receiveSuccessfully() {
		SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0, System.currentTimeMillis()));
		testMessage.setSignature(getKeyPair().sign(hasher.hash(testMessage)));

		RadixSystem radixSystem = spy(testMessage.getSystem());
		doReturn(radixSystem).when(testMessage).getSystem();
		doReturn(EUID.ONE).when(radixSystem).getNID();

		when(this.addressBook.addOrUpdatePeer(any(), any(), any())).thenReturn(peer1);

		final var result = messagePreprocessor.processMessage(transportInfo1, testMessage);
		assertTrue(result.toOptional().isPresent());
	}

	@Test
	public void receiveExpiredMessage() {
		SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
		testMessage.setSignature(getKeyPair().sign(hasher.hash(testMessage)));
		when(testMessage.getTimestamp()).thenReturn(10_000L);

		final var result = messagePreprocessor.processMessage(transportInfo1, testMessage);

		assertTrue(result.toOptional().isEmpty());
		// execution is terminated before message.getSystem() method
		verify(testMessage, times(0)).getSystem();
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

		final var result = messagePreprocessor.processMessage(transportInfo1, testMessage);

		assertTrue(result.toOptional().isEmpty());
	}

	@Test
	public void receiveDisconnectNullZeroSystem() {
		RadixSystem localSystem = getLocalSystem();
		SystemMessage testMessage1 = spy(new SystemMessage(localSystem, 0));
		testMessage1.setSignature(getKeyPair().sign(hasher.hash(testMessage1)));

		when(addressBook.addOrUpdatePeer(any(), eq(localSystem), any())).thenReturn(peer1);

		RadixSystem radixSystem1 = spy(testMessage1.getSystem());
		doReturn(radixSystem1).when(testMessage1).getSystem();
		doReturn(EUID.ZERO).when(radixSystem1).getNID();

		SystemMessage testMessage2 = spy(new SystemMessage(localSystem, 0));
		testMessage2.setSignature(getKeyPair().sign(hasher.hash(testMessage2)));

		RadixSystem radixSystem2 = spy(testMessage2.getSystem());
		doReturn(radixSystem2).when(testMessage2).getSystem();
		doReturn(null).when(radixSystem2).getNID();

		when(addressBook.addOrUpdatePeer(any(), eq(radixSystem1), any())).thenReturn(peer1);
		when(addressBook.addOrUpdatePeer(any(), eq(radixSystem2), any())).thenReturn(peer2);

		final var result1 = messagePreprocessor.processMessage(transportInfo1, testMessage1);
		final var result2 = messagePreprocessor.processMessage(transportInfo2, testMessage2);

		assertTrue(result1.toOptional().isEmpty());
		assertTrue(result2.toOptional().isEmpty());
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

		when(this.addressBook.addOrUpdatePeer(any(), any(), any())).thenReturn(peer1);

		final var result = messagePreprocessor.processMessage(transportInfo1, testMessage);

		assertTrue(result.toOptional().isEmpty());
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

		final var result = messagePreprocessor.processMessage(transportInfo1, testMessage);
		assertTrue(result.toOptional().isEmpty());
	}

	@Test
	public void receiveSystemMessageBadSignature() {
		SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
		RadixSystem radixSystem = spy(testMessage.getSystem());
		doReturn(radixSystem).when(testMessage).getSystem();
		doReturn(getLocalSystem().getNID()).when(radixSystem).getNID();

		final var result = messagePreprocessor.processMessage(transportInfo1, testMessage);

		assertTrue(result.toOptional().isEmpty());
		verify(this.counters, times(1)).increment(CounterType.MESSAGES_INBOUND_BADSIGNATURE);
	}

	@Test
	public void receiveSignedMessageGoodSignature() {
		SignedMessage testMessage = spy(new DummySignedMessage());
		testMessage.setSignature(getKeyPair().sign(hasher.hash(testMessage)));

		final var result = messagePreprocessor.processMessage(transportInfo1, testMessage);

		assertTrue(result.toOptional().isPresent());
	}

	@Test
	public void receiveSignedMessageBadSignature() {
		SignedMessage testMessage = spy(new DummySignedMessage());
		ECKeyPair bogusKey = ECKeyPair.generateNew();
		testMessage.setSignature(bogusKey.sign(hasher.hash(testMessage)));

		final var result = messagePreprocessor.processMessage(transportInfo1, testMessage);

		assertTrue(result.toOptional().isEmpty());
		verify(this.counters, times(1)).increment(CounterType.MESSAGES_INBOUND_BADSIGNATURE);
	}

	@Test
	public void receiveSignedMessageNoPeer() {
		SignedMessage testMessage = new DummySignedMessage();
		testMessage.setSignature(getKeyPair().sign(hasher.hash(testMessage)));

		final var result = messagePreprocessor.processMessage(mock(TransportInfo.class), testMessage);

		assertTrue(result.toOptional().isEmpty());
	}

	@Test
	public void receiveUnsignedMessage() {
		Message testMessage = spy(new DummyMessage());

		final var result = messagePreprocessor.processMessage(transportInfo1, testMessage);

		assertTrue(result.toOptional().isPresent());
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
