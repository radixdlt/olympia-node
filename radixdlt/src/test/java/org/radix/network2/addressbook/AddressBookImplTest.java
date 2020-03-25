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

package org.radix.network2.addressbook;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.radixdlt.universe.Universe;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.events.Events;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.properties.RuntimeProperties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AddressBookImplTest {

	private AtomicInteger broadcastEventCount;
	private AtomicInteger savedPeerCount;
	private AtomicInteger deletedPeerCount;
	private PeerPersistence persistence;
	private Events events;
	private AddressBookImpl addressbook;

	@Before
	public void setUp() throws Exception {
		this.broadcastEventCount = new AtomicInteger(0);
		this.savedPeerCount = new AtomicInteger(0);
		this.deletedPeerCount = new AtomicInteger(0);
		this.persistence = mock(PeerPersistence.class);
		doAnswer(invocation -> {
			savedPeerCount.incrementAndGet();
			return true;
		}).when(this.persistence).savePeer(any());
		doAnswer(invocation -> {
			deletedPeerCount.incrementAndGet();
			return true;
		}).when(this.persistence).deletePeer(any());
		this.events = mock(Events.class);
		doAnswer(invocation -> {
			// ClassCastEx here is a failure, so don't check
			AddressBookEvent abevent = (AddressBookEvent) invocation.getArgument(0);
			this.broadcastEventCount.addAndGet(abevent.peers().size());
			return null;
		}).when(this.events).broadcast(any());
		Universe universe = mock(Universe.class);
		when(universe.getPlanck()).thenReturn(86400L * 1000L);
		this.addressbook = new AddressBookImpl(this.persistence, this.events, universe, mock(RuntimeProperties.class));
	}

	@Test
	public void testAddPeerWithTransport() {
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		PeerWithTransport peer = new PeerWithTransport(transportInfo);

		// Adding should return true, and also fire broadcast event, not saved
		assertTrue(this.addressbook.addPeer(peer));
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(0, this.savedPeerCount.get());

		// Adding again should return false, and also not fire broadcast event, not saved
		assertFalse(this.addressbook.addPeer(peer));
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(0, this.savedPeerCount.get());

		// Should be able to find by transport in address book
		Peer foundPeer = this.addressbook.peer(transportInfo);
		assertSame(peer, foundPeer);

		// Quick check of internal state too
		assertEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
	}

	@Test
	public void testAddPeerWithNid() {
		EUID nid = EUID.ONE;
		PeerWithNid peer = new PeerWithNid(nid);

		// Adding should return true, and also fire broadcast event, saved
		assertTrue(this.addressbook.addPeer(peer));
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(1, this.savedPeerCount.get());

		// Adding again should return false, and also not fire broadcast event, not saved again
		assertFalse(this.addressbook.addPeer(peer));
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(1, this.savedPeerCount.get());

		// Should be able to find by nid in address book
		Peer foundPeer = this.addressbook.peer(nid);
		assertSame(peer, foundPeer);

		// Quick check of internal state too
		assertEmpty(Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testAddPeerWithSystem() {
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		// Prefer to mock RadixSystem.  Not really sure what's going on in there.
		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(EUID.ONE);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		PeerWithSystem peer = new PeerWithSystem(system);

		// Adding should return true, and also fire broadcast event, saved
		assertTrue(this.addressbook.addPeer(peer));
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(1, this.savedPeerCount.get());

		// Adding again should return false, and also not fire broadcast event, not saved again
		assertFalse(this.addressbook.addPeer(peer));
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(1, this.savedPeerCount.get());

		// Should be able to find by nid in address book
		Peer foundPeer1 = this.addressbook.peer(system.getNID());
		assertSame(peer, foundPeer1);

		// Should be able to find by transportInfo in address book
		Peer foundPeer2 = this.addressbook.peer(transportInfo);
		assertSame(peer, foundPeer2);

		// Quick check of internal state too
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testRemovePeerWithNid() {
		EUID nid = EUID.ONE;
		PeerWithNid peer = new PeerWithNid(nid);

		// Adding should return true and broadcast add
		assertTrue(this.addressbook.addPeer(peer));
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(0, this.deletedPeerCount.get());

		assertTrue(this.addressbook.removePeer(peer));
		assertEquals(2, this.broadcastEventCount.get());
		assertEquals(1, this.deletedPeerCount.get());

		// Quick check of internal state too
		assertEmpty(Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testRemovePeerWithTransport() {
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		PeerWithTransport peer = new PeerWithTransport(transportInfo);

		// Adding should return true and broadcast add
		assertTrue(this.addressbook.addPeer(peer));
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(0, this.deletedPeerCount.get());

		assertTrue(this.addressbook.removePeer(peer));
		assertEquals(2, this.broadcastEventCount.get());
		assertEquals(0, this.deletedPeerCount.get()); // Wasn't saved, wasn't deleted

		// Quick check of internal state too
		assertEmpty(Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testUpdatePeer() {
		EUID nid = EUID.ONE;
		PeerWithNid peer = new PeerWithNid(nid);

		assertTrue(this.addressbook.addPeer(peer));
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(1, this.savedPeerCount.get());

		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(nid);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		PeerWithSystem peer2 = new PeerWithSystem(system);
		assertTrue(this.addressbook.updatePeer(peer2));
		assertEquals(2, this.broadcastEventCount.get());
		assertEquals(2, this.savedPeerCount.get());

		// Quick check of internal state too
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testUpdatePeerSystem() {
		EUID nid = EUID.ONE;
		PeerWithNid peer = new PeerWithNid(nid);

		assertTrue(this.addressbook.addPeer(peer));
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(1, this.savedPeerCount.get());

		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(nid);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		Peer peer2 = this.addressbook.updatePeerSystem(peer, system);
		assertNotNull(peer2);
		assertEquals(2, this.broadcastEventCount.get());
		assertEquals(2, this.savedPeerCount.get());

		// Quick check of internal state too
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testUpdatePeerSystemChangedNid() {
		PeerWithNid peer = new PeerWithNid(EUID.ONE);

		assertTrue(this.addressbook.addPeer(peer));
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(1, this.savedPeerCount.get());
		assertEquals(0, this.deletedPeerCount.get());

		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(EUID.TWO);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		Peer peer2 = this.addressbook.updatePeerSystem(peer, system);
		assertNotNull(peer2);
		assertNotSame(peer, peer2);
		assertEquals(3, this.broadcastEventCount.get());
		assertEquals(2, this.savedPeerCount.get());
		assertEquals(1, this.deletedPeerCount.get());

		// Updating again should have no effect
		Peer peer3 = this.addressbook.updatePeerSystem(peer2, system);
		assertSame(peer2, peer3);
		assertEquals(3, this.broadcastEventCount.get());
		assertEquals(2, this.savedPeerCount.get());
		assertEquals(1, this.deletedPeerCount.get());

		// Quick check of internal state too
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testPeerNid() {
		EUID nid = EUID.ONE;

		Peer peer = this.addressbook.peer(nid);
		assertNotNull(peer);
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(1, this.savedPeerCount.get());

		Peer peer2 = this.addressbook.peer(nid);
		assertNotNull(peer2);
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(1, this.savedPeerCount.get());
		assertSame(peer, peer2);

		// Quick check of internal state too
		assertEmpty(Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testPeerTransportInfo() {
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		Peer peer = this.addressbook.peer(transportInfo);
		assertNotNull(peer);
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(0, this.savedPeerCount.get());

		Peer peer2 = this.addressbook.peer(transportInfo);
		assertNotNull(peer2);
		assertEquals(1, this.broadcastEventCount.get());
		assertEquals(0, this.savedPeerCount.get());
		assertSame(peer, peer2);

		// Quick check of internal state too
		assertEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
		assertSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
	}

	@Test
	public void testPeers() {
		TransportInfo transportInfo1 = TransportInfo.of("DUMMY1", StaticTransportMetadata.empty());
		TransportInfo transportInfo2 = TransportInfo.of("DUMMY2", StaticTransportMetadata.empty());
		PeerWithTransport peer1 = new PeerWithTransport(transportInfo1);
		peer1.setTimestamp(Timestamps.ACTIVE, Time.currentTimestamp()); // Now
		PeerWithTransport peer2 = new PeerWithTransport(transportInfo2);
		peer2.setTimestamp(Timestamps.ACTIVE, 0L); // A long time ago

		assertTrue(this.addressbook.addPeer(peer1));
		assertTrue(this.addressbook.addPeer(peer2));

		ImmutableSet<Peer> peers = this.addressbook.peers().collect(ImmutableSet.toImmutableSet());
		assertEquals(2, peers.size());
		assertTrue(peers.contains(peer1));
		assertTrue(peers.contains(peer2));

		// Quick check of internal state too
		assertSize(2, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testRecentPeers() {
		TransportInfo transportInfo1 = TransportInfo.of("DUMMY1", StaticTransportMetadata.empty());
		TransportInfo transportInfo2 = TransportInfo.of("DUMMY2", StaticTransportMetadata.empty());
		PeerWithTransport peer1 = new PeerWithTransport(transportInfo1);
		peer1.setTimestamp(Timestamps.ACTIVE, Time.currentTimestamp()); // Now
		PeerWithTransport peer2 = new PeerWithTransport(transportInfo2);
		peer2.setTimestamp(Timestamps.ACTIVE, 0L); // A long time ago

		assertTrue(this.addressbook.addPeer(peer1));
		assertTrue(this.addressbook.addPeer(peer2));

		ImmutableSet<Peer> peers = this.addressbook.recentPeers().collect(ImmutableSet.toImmutableSet());
		assertEquals(1, peers.size());
		assertTrue(peers.contains(peer1));
		assertFalse(peers.contains(peer2));

		// Quick check of internal state too
		assertSize(2, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testNids() {
		PeerWithNid peer1 = new PeerWithNid(EUID.ONE);
		PeerWithNid peer2 = new PeerWithNid(EUID.TWO);

		assertTrue(this.addressbook.addPeer(peer1));
		assertTrue(this.addressbook.addPeer(peer2));

		ImmutableSet<EUID> nids = this.addressbook.nids().collect(ImmutableSet.toImmutableSet());
		assertEquals(2, nids.size());
		assertTrue(nids.contains(EUID.ONE));
		assertTrue(nids.contains(EUID.TWO));

		// Quick check of internal state too
		assertSize(2, Whitebox.getInternalState(this.addressbook, "peersByNid"));
		assertEmpty(Whitebox.getInternalState(this.addressbook, "peersByInfo"));
	}


	// Type coercion
	private <T, U> void assertEmpty(Map<T, U> map) {
		assertTrue(map.isEmpty());
	}

	// Type coercion
	private <T, U> void assertSize(int size, Map<T, U> map) {
		assertEquals(size, map.size());
	}
}
