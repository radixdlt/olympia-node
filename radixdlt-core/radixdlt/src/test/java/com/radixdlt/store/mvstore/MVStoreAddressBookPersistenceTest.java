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
package com.radixdlt.store.mvstore;

import com.google.common.collect.ImmutableList;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.radix.serialization.RadixTest;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.radix.Radix.*;
import static org.radix.Radix.AGENT;

public class MVStoreAddressBookPersistenceTest extends RadixTest {
	private MVStoreAddressBookPersistence abp;
	private DatabaseEnvironment dbEnv;

	@Before
	public void setUp() {
		dbEnv = new DatabaseEnvironment(getProperties());
		dbEnv.start();

		abp = new MVStoreAddressBookPersistence(getSerialization(), dbEnv, mock(SystemCounters.class));
		abp.reset();
	}

	@After
	public void tearDown() {
		abp.close();
		dbEnv.stop();
	}

	@Test
	public void testReset() {
		abp.start();
		assertTrue(abp.savePeer(makePeer()));

		var peerCount1 = new AtomicInteger(0);
		abp.forEachPersistedPeer(p -> peerCount1.incrementAndGet());
		assertEquals(1, peerCount1.get());

		tearDown();
		setUp();

		var peerCount2 = new AtomicInteger(0);
		abp.forEachPersistedPeer(p -> peerCount2.incrementAndGet());
		assertEquals(0, peerCount2.get());
	}
	@Test
	public void testSavePeer() {
		var pws = makePeer();

		assertTrue(abp.savePeer(pws));
		assertEquals(1, peerCount());
		assertEquals(0L, onlyPeer().getTimestamp(Timestamps.ACTIVE));

		// Update timestamp
		long now = Time.currentTimestamp();
		pws.setTimestamp(Timestamps.ACTIVE, now);
		assertTrue(abp.savePeer(pws));
		assertEquals(1, peerCount());
		assertEquals(now, onlyPeer().getTimestamp(Timestamps.ACTIVE));

		// Add new peer
		var pws2 = makePeer();
		assertTrue(abp.savePeer(pws2));
		assertEquals(2, peerCount());
	}

	@Test
	public void testDeletePeer() {
		abp.start();

		var pws1 = makePeer();
		assertTrue(abp.savePeer(pws1));
		assertEquals(1, peerCount());
		var pws2 = makePeer();
		assertTrue(abp.savePeer(pws2));
		assertEquals(2, peerCount());

		// Delete one, and check that the only one left is the right one
		assertTrue(abp.deletePeer(pws1.getNID()));
		assertEquals(pws2.getNID(), onlyPeer().getNID());

		// Add back the deleted one, and delete the other
		assertTrue(abp.savePeer(pws1));
		assertEquals(2, peerCount());
		assertTrue(abp.deletePeer(pws2.getNID()));
		assertEquals(pws1.getNID(), onlyPeer().getNID());

		// Try to delete something that doesn't exist
		assertFalse(abp.deletePeer(pws2.getNID()));
	}

	private int peerCount() {
		AtomicInteger peercount = new AtomicInteger(0);
		abp.forEachPersistedPeer(p -> peercount.incrementAndGet());
		return peercount.get();
	}

	private Peer onlyPeer() {
		AtomicReference<Peer> peer = new AtomicReference<>();
		abp.forEachPersistedPeer(p -> assertTrue(peer.compareAndSet(null, p)));
		assertNotNull(peer.get());
		return peer.get();
	}
	
	private PeerWithSystem makePeer() {
		var sys = new RadixSystem(
			ECKeyPair.generateNew().getPublicKey(),
			AGENT,
			AGENT_VERSION,
			PROTOCOL_VERSION,
			ImmutableList.of()
		);

		return new PeerWithSystem(sys);
	}
}