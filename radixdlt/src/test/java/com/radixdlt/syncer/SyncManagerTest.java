/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.syncer;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.middleware2.CommittedAtom;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SyncManagerTest {

	private LongSupplier versionProvider;
	private SyncManager syncManager;
	private StateSyncNetwork stateSyncNetwork;
	private AddressBook addressBook;


	private static CommittedAtom buildWithVersion(long version) {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getStateVersion()).thenReturn(version);
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);
		return committedAtom;
	}

	@Before
	public void setUp() {
		List<CommittedAtom> store = new ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			store.add(buildWithVersion(i));
		}
		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		this.addressBook = mock(AddressBook.class);
		versionProvider = () -> store.get(store.size() - 1).getVertexMetadata().getStateVersion();
		syncManager = new SyncManager(
			stateSyncNetwork,
			addressBook,
			(CommittedAtom atom) -> {
			if (atom.getVertexMetadata().getStateVersion() == versionProvider.getAsLong() + 1) {
				store.add(atom);
			} else {
				throw new IllegalArgumentException();
			}
		}, versionProvider, 2, 1);
	}

	@After
	public void tearDown() {
		syncManager.close();
	}

	@Test
	public void basicSynchronization() throws InterruptedException {
		CountDownLatch events = new CountDownLatch(1);
		long targetVersion = 15;
		syncManager.setTargetListener(target -> {
			assertEquals(targetVersion, target);
			events.countDown();
		});
		syncManager.syncToVersion(targetVersion, Collections.singletonList(mock(BFTNode.class)));
		ImmutableList.Builder<CommittedAtom> newAtoms1 = ImmutableList.builder();
		for (int i = 7; i <= 12; i++) {
			newAtoms1.add(buildWithVersion(i));
		}
		syncManager.syncAtoms(newAtoms1.build());
		ImmutableList.Builder<CommittedAtom> newAtoms2 = ImmutableList.builder();
		for (int i = 10; i <= 18; i++) {
			newAtoms2.add(buildWithVersion(i));
		}
		syncManager.syncAtoms(newAtoms2.build());
		assertTrue(events.await(5, TimeUnit.SECONDS));
		assertEquals(18, versionProvider.getAsLong());
	}

	@Test
	public void syncWithLostMessages() throws InterruptedException {
		CountDownLatch events = new CountDownLatch(1);
		long targetVersion = 15;
		syncManager.setTargetListener(target -> fail("Target shouldn't be reached"));
		syncManager.setVersionListener(version -> {
			if (version == 11) {
				assertEquals(2, syncManager.getQueueSize());
				events.countDown();
			} else if (version > 11) {
				fail("Version " + version + " should not be reached!");
			}
		});
		syncManager.syncToVersion(targetVersion, Collections.singletonList(mock(BFTNode.class)));
		ImmutableList.Builder<CommittedAtom> newAtoms1 = ImmutableList.builder();
		for (int i = 7; i <= 11; i++) {
			newAtoms1.add(buildWithVersion(i));
		}
		newAtoms1.add(buildWithVersion(13));
		newAtoms1.add(buildWithVersion(15));
		syncManager.syncAtoms(newAtoms1.build());
		assertTrue(events.await(5, TimeUnit.SECONDS));
		assertEquals(11, versionProvider.getAsLong());
	}

	@Test
	public void requestSent() throws InterruptedException {
		long targetVersion = 15;
		BFTNode node = mock(BFTNode.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(mock(EUID.class));
		when(node.getKey()).thenReturn(key);
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		when(addressBook.peer(any(EUID.class))).thenReturn(Optional.of(peer));
		syncManager.syncToVersion(targetVersion, Collections.singletonList(node));
		verify(stateSyncNetwork, timeout(5000).times(1)).sendSyncRequest(any(), eq(10L));
		verify(stateSyncNetwork, timeout(5000).times(1)).sendSyncRequest(any(), eq(12L));
		verify(stateSyncNetwork, timeout(5000).times(1)).sendSyncRequest(any(), eq(14L));
	}

	@Test
	public void atomsListPruning() throws InterruptedException {
		CountDownLatch events = new CountDownLatch(1);
		syncManager.setVersionListener(version -> {
			long mxVersion = 10L + syncManager.getMaxAtomsQueueSize();
			if (version == mxVersion) {
				assertEquals(0, syncManager.getQueueSize());
				events.countDown();
			}
		});
		ImmutableList.Builder<CommittedAtom> newAtoms = ImmutableList.builder();
		for (int i = 1000; i >= 1; i--) {
			newAtoms.add(buildWithVersion(i));
		}
		syncManager.syncAtoms(newAtoms.build());
		assertTrue(events.await(5, TimeUnit.SECONDS));
		assertEquals(10L + syncManager.getMaxAtomsQueueSize(), versionProvider.getAsLong());
	}

	@Test
	public void applyAtoms() throws InterruptedException {
		CountDownLatch versions = new CountDownLatch(1);
		syncManager.setVersionListener(version -> {
			assertEquals(20, version);
			versions.countDown();
		});
		ImmutableList.Builder<CommittedAtom> newAtoms = ImmutableList.builder();
		for (int i = 5; i <= 20; i++) {
			newAtoms.add(buildWithVersion(i));
		}
		syncManager.syncAtoms(newAtoms.build());
		assertTrue(versions.await(5, TimeUnit.SECONDS));
		assertEquals(20, versionProvider.getAsLong());
	}

	/*
	@Test
	public void targetVersion() throws InterruptedException {
		CountDownLatch versions = new CountDownLatch(1);
		syncManager.setVersionListener(version -> {
			if (version == 20) {
				versions.countDown();
			}
		});
		ImmutableList.Builder<CommittedAtom> newAtoms = ImmutableList.builder();
		for (int i = 10; i <= 20; i++) {
			newAtoms.add(buildWithVersion(i));
		}
		syncManager.syncAtoms(newAtoms.build());
		assertTrue(versions.await(5, TimeUnit.SECONDS));

		final Semaphore sem = new Semaphore(0);

		assertEquals(-1, syncManager.getTargetVersion());
		long targetVersion = 15;
		syncManager.syncToVersion(targetVersion, reqId -> sem.release());
		// Already synced up to 20, so no request should happen
		assertFalse(sem.tryAcquire(100, TimeUnit.MILLISECONDS));
		assertEquals(-1, syncManager.getTargetVersion());

		syncManager.syncToVersion(targetVersion * 2, reqId -> sem.release());
		assertTrue(sem.tryAcquire(100, TimeUnit.MILLISECONDS));
		assertEquals(30, syncManager.getTargetVersion());

		syncManager.syncToVersion(targetVersion - 5, reqId -> sem.release());
		assertTrue(sem.tryAcquire(1, TimeUnit.SECONDS));
		assertEquals(30, syncManager.getTargetVersion());
	}
	 */
}