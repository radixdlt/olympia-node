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

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.properties.RuntimeProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.observers.TestObserver;

public class AddressBookImplTest {

	private AtomicInteger savedPeerCount;
	private AtomicInteger deletedPeerCount;
	private AddressBookImpl addressbook;
	private TestObserver<AddressBookEvent> peersObserver;

	@Before
	public void setUp() {
		this.savedPeerCount = new AtomicInteger(0);
		this.deletedPeerCount = new AtomicInteger(0);
		// No danger of resources not being closed with mock
		@SuppressWarnings("resource")
		PeerPersistence persistence = mock(PeerPersistence.class);
		doAnswer(invocation -> {
			savedPeerCount.incrementAndGet();
			return true;
		}).when(persistence).savePeer(any());
		doAnswer(invocation -> {
			deletedPeerCount.incrementAndGet();
			return true;
		}).when(persistence).deletePeer(any());
		RuntimeProperties properties = mock(RuntimeProperties.class);
		doReturn(60_000L).when(properties).get(eq("addressbook.recency_ms"), anyLong());
		this.addressbook = new AddressBookImpl(persistence, properties, System::currentTimeMillis);
		this.peersObserver = TestObserver.create();
		this.addressbook.peerUpdates()
			.subscribe(this.peersObserver);
		this.peersObserver.awaitCount(1)
			.assertValue(e -> e instanceof PeersAddedEvent && e.peers().isEmpty());
	}

	@After
	public void tearDown() throws IOException {
		// Ensure emitters are properly removed on disposal
		this.peersObserver.dispose();
		assertSetEmpty(Whitebox.getInternalState(this.addressbook, "emitters"));
	    addressbook.close();
	}

	@Test
	public void testAddPeerWithSystem() {
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		// Prefer to mock RadixSystem.  Not really sure what's going on in there.
		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(EUID.ONE);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));

		// Adding should return true, and also fire broadcast event, saved
		PeerWithSystem peer = this.addressbook.addOrUpdatePeer(Optional.empty(), system, transportInfo);
		assertNotNull(peer);
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, e -> e instanceof PeersAddedEvent);
		assertEquals(1, this.savedPeerCount.get());

		// Adding again should not fire broadcast event, not saved again
		assertNotNull(this.addressbook.addOrUpdatePeer(Optional.of(peer), system, transportInfo));
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2); // But no new ones
		assertEquals(1, this.savedPeerCount.get());

		// Should be able to find by nid in address book
		Optional<PeerWithSystem> foundPeer1 = this.addressbook.peer(system.getNID());
		assertTrue(foundPeer1.isPresent());
		assertEquals(peer, foundPeer1.get());

		// Should be able to find by transportInfo in address book
		Optional<PeerWithSystem> foundPeer2 = this.addressbook.peer(transportInfo);
		assertTrue(foundPeer2.isPresent());
		assertEquals(peer, foundPeer2.get());

		// Quick check of internal state too
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersBySource"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
	}

	@Test
	public void testUpdatePeerSameSystem() throws InterruptedException, IOException {
		EUID nid = EUID.ONE;
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(nid);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		TransportInfo source = mock(TransportInfo.class);

		assertNotNull(this.addressbook.addOrUpdatePeer(Optional.empty(), system, source));
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, v -> v instanceof PeersAddedEvent);
		assertEquals(1, this.savedPeerCount.get());
		assertSetSize(1, Whitebox.getInternalState(this.addressbook, "emitters"));

		PeerWithSystem peer2 = this.addressbook.addOrUpdatePeer(Optional.empty(), system, source);
		assertNotNull(peer2);
		assertEquals(1, this.savedPeerCount.get());
		this.addressbook.close();
		this.peersObserver.await(100, TimeUnit.MILLISECONDS); // Just in case something arrives
		this.peersObserver
			.assertNoErrors()
			.assertComplete()
			.assertValueCount(2)
			.assertValueAt(1, v -> v instanceof PeersAddedEvent);

		// Quick check of internal state too
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersBySource"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testUpdatePeerSystemChangedNid() {
		EUID nid = EUID.ONE;
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		RadixSystem system1 = mock(RadixSystem.class);
		when(system1.getNID()).thenReturn(nid);
		when(system1.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		TransportInfo source = mock(TransportInfo.class);

		PeerWithSystem peer = this.addressbook.addOrUpdatePeer(Optional.empty(), system1, source);
		assertNotNull(peer);

		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, v -> v instanceof PeersAddedEvent);
		assertEquals(1, this.savedPeerCount.get());
		assertEquals(0, this.deletedPeerCount.get());

		RadixSystem system2 = mock(RadixSystem.class);
		when(system2.getNID()).thenReturn(EUID.TWO);
		when(system2.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		PeerWithSystem peer2 = this.addressbook.addOrUpdatePeer(Optional.of(peer), system2, source);
		assertNotNull(peer2);
		assertNotSame(peer, peer2);
		this.peersObserver.awaitCount(4)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(4)
			.assertValueAt(2, v -> v instanceof PeersAddedEvent)
			.assertValueAt(3, v -> v instanceof PeersRemovedEvent);
		assertEquals(2, this.savedPeerCount.get());
		assertEquals(1, this.deletedPeerCount.get());

		// Updating again should have no effect
		Peer peer3 = this.addressbook.addOrUpdatePeer(Optional.of(peer2), system2, source);
		assertSame(peer2, peer3);
		this.peersObserver.awaitCount(4)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(4);
		assertEquals(2, this.savedPeerCount.get());
		assertEquals(1, this.deletedPeerCount.get());

		// Quick check of internal state too
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersBySource"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testPeers() {
		TransportInfo transportInfo1 = TransportInfo.of("DUMMY1", StaticTransportMetadata.empty());
		TransportInfo transportInfo2 = TransportInfo.of("DUMMY2", StaticTransportMetadata.empty());
		RadixSystem system1 = mock(RadixSystem.class);
		when(system1.getNID()).thenReturn(EUID.ONE);
		when(system1.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo1));
		RadixSystem system2 = mock(RadixSystem.class);
		when(system2.getNID()).thenReturn(EUID.TWO);
		when(system2.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo2));

		PeerWithSystem peer1 = this.addressbook.addOrUpdatePeer(Optional.empty(), system1, transportInfo1);
		PeerWithSystem peer2 = this.addressbook.addOrUpdatePeer(Optional.empty(), system2, transportInfo2);

		peer1.setTimestamp(Timestamps.ACTIVE, Time.currentTimestamp()); // Now
		peer2.setTimestamp(Timestamps.ACTIVE, 0L); // A long time ago

		ImmutableSet<Peer> peers = this.addressbook.peers().collect(ImmutableSet.toImmutableSet());
		assertEquals(2, peers.size());
		assertTrue(peers.contains(peer1));
		assertTrue(peers.contains(peer2));

		// Quick check of internal state too
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersBySource"));
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testRecentPeers() {
		TransportInfo transportInfo1 = TransportInfo.of("DUMMY1", StaticTransportMetadata.empty());
		TransportInfo transportInfo2 = TransportInfo.of("DUMMY2", StaticTransportMetadata.empty());
		RadixSystem system1 = mock(RadixSystem.class);
		when(system1.getNID()).thenReturn(EUID.ONE);
		when(system1.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo1));
		RadixSystem system2 = mock(RadixSystem.class);
		when(system2.getNID()).thenReturn(EUID.TWO);
		when(system2.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo2));

		PeerWithSystem peer1 = this.addressbook.addOrUpdatePeer(Optional.empty(), system1, transportInfo1);
		PeerWithSystem peer2 = this.addressbook.addOrUpdatePeer(Optional.empty(), system2, transportInfo2);

		peer1.setTimestamp(Timestamps.ACTIVE, Time.currentTimestamp()); // Now
		peer2.setTimestamp(Timestamps.ACTIVE, 0L); // A long time ago

		ImmutableSet<Peer> peers = this.addressbook.recentPeers().collect(ImmutableSet.toImmutableSet());
		assertEquals(1, peers.size());
		assertTrue(peers.contains(peer1));
		assertFalse(peers.contains(peer2));

		// Quick check of internal state too
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersBySource"));
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testNids() {
		TransportInfo transportInfo1 = TransportInfo.of("DUMMY1", StaticTransportMetadata.empty());
		TransportInfo transportInfo2 = TransportInfo.of("DUMMY2", StaticTransportMetadata.empty());
		RadixSystem system1 = mock(RadixSystem.class);
		when(system1.getNID()).thenReturn(EUID.ONE);
		when(system1.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo1));
		RadixSystem system2 = mock(RadixSystem.class);
		when(system2.getNID()).thenReturn(EUID.TWO);
		when(system2.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo2));

		assertNotNull(this.addressbook.addOrUpdatePeer(Optional.empty(), system1, transportInfo1));
		assertNotNull(this.addressbook.addOrUpdatePeer(Optional.empty(), system2, transportInfo2));

		ImmutableSet<EUID> nids = this.addressbook.nids().collect(ImmutableSet.toImmutableSet());
		assertEquals(2, nids.size());
		assertTrue(nids.contains(EUID.ONE));
		assertTrue(nids.contains(EUID.TWO));

		// Quick check of internal state too
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersByNid"));
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersBySource"));
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
	}

	@Test
	public void testSourceChanged() {
		TransportInfo systemTransport = TransportInfo.of("SYSTEM", StaticTransportMetadata.empty());
		TransportInfo sourceTransport1 = TransportInfo.of("SOURCE1", StaticTransportMetadata.empty());
		TransportInfo sourceTransport2 = TransportInfo.of("SOURCE2", StaticTransportMetadata.empty());

		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(EUID.ONE);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(systemTransport));

		// Add initial peer
		PeerWithSystem newPeer1 = this.addressbook.addOrUpdatePeer(Optional.empty(), system, sourceTransport1);
		assertNotNull(newPeer1); // Should be added OK

		// Now update source, without changing other details
		assertNotNull(this.addressbook.addOrUpdatePeer(Optional.of(newPeer1), system, sourceTransport2));

		// Should now be able to find by updated source...
		assertThat(this.addressbook.peer(sourceTransport2)).isNotEmpty();

		// ...and trying to find by old source should not be possible.
		assertThat(this.addressbook.peer(sourceTransport1)).isEmpty();
	}

	@Test
	public void testHasBftNodePeer() {
		final var key1 = mock(ECPublicKey.class);
		final var key2 = mock(ECPublicKey.class);

		TransportInfo transportInfo1 = TransportInfo.of("DUMMY1", StaticTransportMetadata.empty());
		RadixSystem system1 = mock(RadixSystem.class);
		when(system1.getKey()).thenReturn(key1);
		when(system1.getNID()).thenReturn(EUID.ONE);
		when(system1.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo1));

		PeerWithSystem peer1 = this.addressbook.addOrUpdatePeer(Optional.empty(), system1, transportInfo1);

		peer1.setTimestamp(Timestamps.ACTIVE, Time.currentTimestamp()); // Now

		final var bftNode1 = mock(BFTNode.class);
		when(bftNode1.getKey()).thenReturn(key1);
		assertTrue(this.addressbook.hasBftNodePeer(bftNode1));

		final var bftNode2 = mock(BFTNode.class);
		when(bftNode2.getKey()).thenReturn(key2);
		assertFalse(this.addressbook.hasBftNodePeer(bftNode2));
	}

	// Type coercion
	private <T, U> void assertMapSize(int size, Map<T, U> map) {
		assertEquals(size, map.size());
	}

	// Type coercion
	private <T> void assertSetEmpty(Set<T> set) {
		assertTrue(set.isEmpty());
	}

	// Type coercion
	private <T> void assertSetSize(int size, Set<T> set) {
		assertEquals(size, set.size());
	}
}
