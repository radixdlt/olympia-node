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

package com.radixdlt.network.addressbook;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.network.transport.udp.UDPConstants;
import com.radixdlt.properties.RuntimeProperties;

import org.assertj.core.api.SoftAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.radix.network.discovery.BootstrapDiscovery;
import org.radix.network.messages.GetPeersMessage;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.PeerPongMessage;
import org.radix.network.messages.PeersMessage;
import org.radix.network.messaging.Message;
import org.radix.serialization.RadixTest;
import org.radix.serialization.TestSetupUtils;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class PeerManagerTest extends RadixTest {
	private PeerManager peerManager;
	private AddressBook addressBook;
	private BootstrapDiscovery bootstrapDiscovery;
	private PeerWithSystem peer1;
	private PeerWithSystem peer2;
	private PeerWithSystem peer3;
	private PeerWithSystem peer4;
	private TransportInfo transportInfo3;
	private TransportInfo transportInfo4;
	private Multimap<Peer, Message> peerMessageMultimap;

	@BeforeClass
	public static void beforeClass() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Before
	public void setUp() {
		RuntimeProperties properties = getProperties();

		when(properties.get(eq("network.peers.heartbeat.delay"), anyInt())).thenReturn(100);
		when(properties.get(eq("network.peers.heartbeat.interval"), anyInt())).thenReturn(200);

		when(properties.get(eq("network.peers.broadcast.delay"), anyInt())).thenReturn(100);
		when(properties.get(eq("network.peers.broadcast.interval"), anyInt())).thenReturn(200);

		when(properties.get(eq("network.peers.probe.delay"), anyInt())).thenReturn(100);
		when(properties.get(eq("network.peers.probe.interval"), anyInt())).thenReturn(200);
		when(properties.get(eq("network.peers.probe.frequency"), anyInt())).thenReturn(300);

		when(properties.get(eq("network.peers.discover.delay"), anyInt())).thenReturn(100);
		when(properties.get(eq("network.peers.discover.interval"), anyInt())).thenReturn(200);

		when(properties.get(eq("network.peers.message.batch.size"), anyInt())).thenReturn(2);

		when(properties.get(eq("network.peers.recency_ms"), anyLong())).thenReturn(60_000L);

		PeerManagerConfiguration config = PeerManagerConfiguration.fromRuntimeProperties(properties);
		peerMessageMultimap = LinkedListMultimap.create();
		MessageCentral messageCentral = mock(MessageCentral.class);
		SecureRandom rng = mock(SecureRandom.class);

		Map<Class<Message>, MessageListener<Message>> messageListenerRegistry = new HashMap<>();
		doAnswer(invocation -> {
			messageListenerRegistry.put(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(messageCentral).addListener(any(), any());

		ArgumentCaptor<Peer> peerArgumentCaptor = ArgumentCaptor.forClass(Peer.class);
		ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
		doAnswer(invocation -> {
			peerMessageMultimap.put(invocation.getArgument(0), invocation.getArgument(1));
			MessageListener<Message> messageListener = messageListenerRegistry.get(invocation.getArgument(1).getClass());
			messageListener.handleMessage(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(messageCentral).send(peerArgumentCaptor.capture(), messageArgumentCaptor.capture());


		TransportMetadata transportMetadata1 = TransportMetadata.create(ImmutableMap.of("host", "192.168.0.1"));
		TransportInfo transportInfo1 = TransportInfo.of(UDPConstants.NAME, transportMetadata1);
		TransportMetadata transportMetadata2 = TransportMetadata.create(ImmutableMap.of("host", "192.168.0.2"));
		TransportInfo transportInfo2 = TransportInfo.of(UDPConstants.NAME, transportMetadata2);
		TransportMetadata transportMetadata3 = TransportMetadata.create(ImmutableMap.of("host", "192.168.0.3"));
		transportInfo3 = TransportInfo.of(UDPConstants.NAME, transportMetadata3);
		TransportMetadata transportMetadata4 = TransportMetadata.create(ImmutableMap.of("host", "192.168.0.4"));
		transportInfo4 = TransportInfo.of(UDPConstants.NAME, transportMetadata4);

		RadixSystem radixSystem1 = spy(new RadixSystem());
		when(radixSystem1.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo1));
		when(radixSystem1.getNID()).thenReturn(EUID.ONE);
		peer1 = spy(new PeerWithSystem(radixSystem1));
		when(peer1.getTimestamp(Timestamps.ACTIVE)).thenAnswer(invocation -> System.currentTimeMillis());

		RadixSystem radixSystem2 = spy(new RadixSystem());
		when(radixSystem2.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo2));
		when(radixSystem2.getNID()).thenReturn(EUID.TWO);
		peer2 = spy(new PeerWithSystem(radixSystem2));
		when(peer2.getTimestamp(Timestamps.ACTIVE)).thenAnswer(invocation -> System.currentTimeMillis());

		RadixSystem radixSystem3 = spy(new RadixSystem());
		when(radixSystem3.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo3));
		when(radixSystem3.getNID()).thenReturn(new EUID(3));
		peer3 = spy(new PeerWithSystem(radixSystem3));
		when(peer3.getTimestamp(Timestamps.ACTIVE)).thenAnswer(invocation -> System.currentTimeMillis());

		RadixSystem radixSystem4 = spy(new RadixSystem());
		when(radixSystem4.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo4));
		when(radixSystem4.getNID()).thenReturn(new EUID(4));
		peer4 = spy(new PeerWithSystem(radixSystem4));
		when(peer4.getTimestamp(Timestamps.ACTIVE)).thenAnswer(invocation -> System.currentTimeMillis());

		addressBook = mock(AddressBook.class);
		when(addressBook.peer(transportInfo1)).thenReturn(Optional.of(peer1));
		when(addressBook.peer(transportInfo2)).thenReturn(Optional.of(peer2));
		when(addressBook.peer(transportInfo3)).thenReturn(Optional.of(peer3));
		when(addressBook.peer(transportInfo4)).thenReturn(Optional.of(peer4));

		bootstrapDiscovery = mock(BootstrapDiscovery.class);
		peerManager = new PeerManager(
			config,
			addressBook,
			messageCentral,
			bootstrapDiscovery,
			rng,
			getLocalSystem(),
			properties,
			getUniverse()
		);

		// Ignore interrupted flag from other tests
		Thread.interrupted();
	}

	@After
	public void tearDown() {
		peerManager.stop();
	}

	@Test
	public void heartbeatPeersTest() throws InterruptedException {
		when(addressBook.recentPeers()).thenAnswer((Answer<Stream<Peer>>) invocation -> Stream.of(peer1, peer2));

		Semaphore semaphore = new Semaphore(0);
		// allow peer manager to run 1 sec
		peerManager.start();
		Timer t = new Timer();
		t.schedule(new TimerTask() {
			@Override
			public void run() {
				peerManager.stop();
				semaphore.release();
			}
		}, 1000);
		try {
			semaphore.acquire();

			List<SystemMessage> peer1SystemMessages = peerMessageMultimap.get(peer1).stream()
					.filter(message -> message instanceof SystemMessage).map(message -> (SystemMessage) message)
					.collect(Collectors.toList());
			List<SystemMessage> peer2SystemMessages = peerMessageMultimap.get(peer2).stream()
					.filter(message -> message instanceof SystemMessage).map(message -> (SystemMessage) message)
					.collect(Collectors.toList());

			// in 1 sec of execution with network.peers.heartbeat.delay = 100 and network.peers.heartbeat.interval = 200
			// heartbeat message should be sent 4 times for each peer (1000-100)/200 = 4.5
			// message delivery could be late and last few messages of SystemMessage could be lost or
			// could be executed more times as peerManager started before we scheduling stop operation and some messages
			// could be sent before moment when we are starting to count
			SoftAssertions.assertSoftly(softly -> {
				softly.assertThat(peer1SystemMessages.size()).isCloseTo(4, offset(1));
				softly.assertThat(peer2SystemMessages.size()).isCloseTo(4, offset(1));
			});
		} finally {
			t.cancel();
		}
	}

	@Test
	public void peersHousekeepingTest() throws InterruptedException {
		when(addressBook.recentPeers()).thenAnswer((Answer<Stream<Peer>>) invocation -> Stream.of(peer1, peer2));
		when(addressBook.peers()).thenAnswer((Answer<Stream<Peer>>) invocation -> Stream.of(peer1, peer2, peer3, peer4));
		getPeersMessageTest(peer1, peer2, false);
	}

	@Test
	public void probeTaskTest() throws InterruptedException {
		when(addressBook.peers()).thenAnswer((Answer<Stream<Peer>>) invocation -> Stream.of(peer1, peer2));
		Semaphore semaphore = new Semaphore(0);
		peerManager.start();
		// allow peer manager to run 1 sec
		Timer t = new Timer();
		t.schedule(new TimerTask() {
			@Override
			public void run() {
				peerManager.stop();
				semaphore.release();
			}
		}, 1000);
		try {
			semaphore.acquire();

			List<PeerPingMessage> peer1PeerPingMessages = peerMessageMultimap.get(peer1).stream()
					.filter(message -> message instanceof PeerPingMessage).map(message -> (PeerPingMessage) message)
					.collect(Collectors.toList());
			List<PeerPingMessage> peer2PeerPingMessages = peerMessageMultimap.get(peer2).stream()
					.filter(message -> message instanceof PeerPingMessage).map(message -> (PeerPingMessage) message)
					.collect(Collectors.toList());
			List<PeerPongMessage> peer1PeerPongMessages = peerMessageMultimap.get(peer1).stream()
					.filter(message -> message instanceof PeerPongMessage).map(message -> (PeerPongMessage) message)
					.collect(Collectors.toList());
			List<PeerPongMessage> peer2PeerPongMessages = peerMessageMultimap.get(peer2).stream()
					.filter(message -> message instanceof PeerPongMessage).map(message -> (PeerPongMessage) message)
					.collect(Collectors.toList());

			SoftAssertions.assertSoftly(softly -> {
				// in 1 sec of execution with network.peer.probe.delay = 100 and network.peer.probe.interval = 200
				// Ping message will be initiated by executor at least 4 times (1000-100)/200 = 4.5
				// but because of network.peer.probe.frequency = 300 parameter we expect 2 requests only
				// message delivery could be late and last few messages of PeerPing/PeerPong could be lost or
				// could be executed more times as peerManager started before we scheduling stop operation and some messages
				// could be sent before moment when we are starting to count
				softly.assertThat(peer1PeerPingMessages.size()).isCloseTo(2, offset(1));
				softly.assertThat(peer2PeerPingMessages.size()).isCloseTo(2, offset(1));

				// each ping will receive pong message
				softly.assertThat(peer1PeerPongMessages.size()).isCloseTo(2, offset(1));
				softly.assertThat(peer2PeerPongMessages.size()).isCloseTo(2, offset(1));

			});
		} finally {
			t.cancel();
		}
	}

	@Test
	public void discoverPeersTest() throws InterruptedException {
		when(bootstrapDiscovery.discoveryHosts()).thenReturn(ImmutableSet.of(transportInfo3, transportInfo4));
		when(addressBook.peers()).thenAnswer(invocation -> Stream.of(peer1, peer2, peer3, peer4));
		when(addressBook.recentPeers()).thenAnswer(invocation -> Stream.of(peer3, peer4));
		getPeersMessageTest(peer3, peer4, false);
	}

	private void getPeersMessageTest(PeerWithSystem peer1, PeerWithSystem peer2, boolean probeAll) throws InterruptedException {
		Semaphore semaphore = new Semaphore(0);
		peerManager.start();
		// allow peer manager to run 1 sec
		Timer t = new Timer();
		t.schedule(new TimerTask() {
			@Override
			public void run() {
				peerManager.stop();
				semaphore.release();
			}
		}, 1000);
		try {
			semaphore.acquire();

			List<GetPeersMessage> peer1GetPeersMessages = peerMessageMultimap.get(peer1).stream()
					.filter(message -> message instanceof GetPeersMessage).map(message -> (GetPeersMessage) message)
					.collect(Collectors.toList());
			List<GetPeersMessage> peer2GetPeersMessages = peerMessageMultimap.get(peer2).stream()
					.filter(message -> message instanceof GetPeersMessage).map(message -> (GetPeersMessage) message)
					.collect(Collectors.toList());

			List<PeersMessage> peer1PeersMessage = peerMessageMultimap.get(peer1).stream()
					.filter(message -> message instanceof PeersMessage).map(message -> (PeersMessage) message)
					.collect(Collectors.toList());
			List<PeersMessage> peer2PeersMessage = peerMessageMultimap.get(peer2).stream()
					.filter(message -> message instanceof PeersMessage).map(message -> (PeersMessage) message)
					.collect(Collectors.toList());

			SoftAssertions.assertSoftly(softly -> {

				// in 1 sec of execution with network.peers.broadcast.delay = 100 and network.peers.broadcast.interval = 200
				// if probeAll = false then only one peer will be selected from recentPeers to send message randomly.
				// GetPeersMessage message should be sent 4 times (1000-100)/200 = 4
				// if probeAll = true then all peers from list will be discovered GetPeersMessage message should be sent 8 times
				// (1000-100)/200 = 4 then 4*2 = 8
				// message delivery could be late and last few messages of GetPeerMessage/PeerMessage could be lost or
				// could be executed more times as peerManager started before we scheduling stop operation and some messages
				// could be sent before moment when we are starting to count
				int expectedNumberOfGetPeersMessages = probeAll ? 8 : 4;
				// number of responses is equal to 2 * number of requests
				// because we are using network.peers.message.batch.size = 2
				int expectedNumberOfPeersMessage = expectedNumberOfGetPeersMessages * 2;
				softly.assertThat(peer1GetPeersMessages.size() + peer2GetPeersMessages.size())
					.isCloseTo(expectedNumberOfGetPeersMessages, offset(2));
				softly.assertThat(peer1PeersMessage.size() + peer2PeersMessage.size())
					.isCloseTo(expectedNumberOfPeersMessage, offset(2 * 2));

				// number of responses is equal to 2 * number of requests
				// because we are using network.peers.message.batch.size = 2
				// each response should have 3 peer so we will have 2 message for each response with 2 and 1 peer accordingly
				// message delivery could be late and last few messages of GetPeerMessage/PeerMessage could be lost or
				// could be executed more times as peerManager started before we scheduling stop operation and some messages
				// could be sent before moment when we are starting to count
				softly.assertThat(peer1GetPeersMessages.size() * 2).isCloseTo(peer1PeersMessage.size(), offset(2));
				softly.assertThat(peer2GetPeersMessages.size() * 2).isCloseTo(peer2PeersMessage.size(), offset(2));

				// each response exclude self
				// because we are using network.peers.message.batch.size = 2 batch is <= 2
				// each response should have 3 peer so we will have 2 message for each response with 2 and 1 peer accordingly
				// total number of peers = number of getPeers requests * 3
				AtomicInteger peerNumber = new AtomicInteger(0);
				peer1PeersMessage.forEach(message -> {
					softly.assertThat(message.getPeers()).doesNotContain(peer1);
					softly.assertThat(message.getPeers().size()).isBetween(1, 2);
					peerNumber.addAndGet(message.getPeers().size());
				});
				// message delivery could be late and last few messages of GetPeerMessage/PeerMessage could be lost or
				// could be executed more times as peerManager started before we scheduling stop operation and some messages
				// could be sent before moment when we are starting to count
				softly.assertThat(peerNumber.get()).isCloseTo(peer1GetPeersMessages.size() * 3, offset(3));

				peerNumber.set(0);
				peer2PeersMessage.forEach(message -> {
					softly.assertThat(message.getPeers()).doesNotContain(peer2);
					softly.assertThat(message.getPeers().size()).isBetween(1, 2);
					peerNumber.addAndGet(message.getPeers().size());
				});
				// message delivery could be late and last few messages of GetPeerMessage/PeerMessage could be lost or
				// could be executed more times as peerManager started before we scheduling stop operation and
				// some messages could be sent before moment when we are starting to count
				softly.assertThat(peerNumber.get()).isCloseTo(peer2GetPeersMessages.size() * 3, offset(3));
			});
		} finally {
			t.cancel();
		}
	}
}