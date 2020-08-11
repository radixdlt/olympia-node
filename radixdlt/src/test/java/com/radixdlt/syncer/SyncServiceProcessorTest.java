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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.execution.RadixEngineExecutor;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.syncer.SyncServiceProcessor.SyncTimeoutScheduler;
import com.radixdlt.syncer.SyncServiceProcessor.SyncedAtomSender;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class SyncServiceProcessorTest {

	private StateSyncNetwork stateSyncNetwork;
	private SyncServiceProcessor syncServiceProcessor;
	private AddressBook addressBook;
	private RadixEngineExecutor executor;
	private SyncedAtomSender syncedAtomSender;
	private SyncTimeoutScheduler syncTimeoutScheduler;

	private static CommittedAtom buildWithVersion(long version) {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getStateVersion()).thenReturn(version);
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);
		return committedAtom;
	}

	@Before
	public void setUp() {
		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		this.addressBook = mock(AddressBook.class);
		this.executor = mock(RadixEngineExecutor.class);
		this.syncedAtomSender = mock(SyncedAtomSender.class);
		this.syncTimeoutScheduler = mock(SyncTimeoutScheduler.class);
		this.syncServiceProcessor = new SyncServiceProcessor(
			executor,
			stateSyncNetwork,
			addressBook,
			syncedAtomSender,
			syncTimeoutScheduler,
			2,
			1
		);
	}

	@Test
	public void basicSynchronization() {
		final long currentVersion = 6;
		final long targetVersion = 15;

		BFTNode node = mock(BFTNode.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(mock(EUID.class));
		when(node.getKey()).thenReturn(key);
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		when(addressBook.peer(any(EUID.class))).thenReturn(Optional.of(peer));
		LocalSyncRequest request = new LocalSyncRequest(targetVersion, currentVersion, ImmutableList.of(node));
		syncServiceProcessor.processLocalSyncRequest(request);

		ImmutableList.Builder<CommittedAtom> newAtoms1 = ImmutableList.builder();
		for (int i = 7; i <= 12; i++) {
			newAtoms1.add(buildWithVersion(i));
		}
		syncServiceProcessor.processSyncResponse(newAtoms1.build());
		ImmutableList.Builder<CommittedAtom> newAtoms2 = ImmutableList.builder();
		for (int i = 10; i <= 18; i++) {
			newAtoms2.add(buildWithVersion(i));
		}
		syncServiceProcessor.processSyncResponse(newAtoms2.build());

		verify(syncedAtomSender, times((int) (18 - currentVersion))).sendSyncedAtom(any());
	}

	@Test
	public void syncWithLostMessages() {
		final long currentVersion = 6;
		final long targetVersion = 15;
		BFTNode node = mock(BFTNode.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(mock(EUID.class));
		when(node.getKey()).thenReturn(key);
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		when(addressBook.peer(any(EUID.class))).thenReturn(Optional.of(peer));
		LocalSyncRequest request = new LocalSyncRequest(targetVersion, currentVersion, ImmutableList.of(node));
		syncServiceProcessor.processLocalSyncRequest(request);
		ImmutableList.Builder<CommittedAtom> newAtoms1 = ImmutableList.builder();
		for (int i = 7; i <= 11; i++) {
			newAtoms1.add(buildWithVersion(i));
		}
		newAtoms1.add(buildWithVersion(13));
		newAtoms1.add(buildWithVersion(15));
		syncServiceProcessor.processSyncResponse(newAtoms1.build());
		verify(syncedAtomSender, never()).sendSyncedAtom(argThat(a -> a.getVertexMetadata().getStateVersion() > 11));
	}

	@Test
	public void requestSent() {
		final long currentVersion = 10;
		final long targetVersion = 15;
		BFTNode node = mock(BFTNode.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(mock(EUID.class));
		when(node.getKey()).thenReturn(key);
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		when(addressBook.peer(any(EUID.class))).thenReturn(Optional.of(peer));
		LocalSyncRequest request = new LocalSyncRequest(targetVersion, currentVersion, ImmutableList.of(node));
		syncServiceProcessor.processLocalSyncRequest(request);
		verify(stateSyncNetwork, times(1)).sendSyncRequest(any(), eq(10L));
		verify(stateSyncNetwork, times(1)).sendSyncRequest(any(), eq(12L));
		verify(stateSyncNetwork, times(1)).sendSyncRequest(any(), eq(14L));
	}

	/*
	@Test
	public void atomsListPruning() {
		ImmutableList.Builder<CommittedAtom> newAtoms = ImmutableList.builder();
		for (int i = 1000; i >= 1; i--) {
			newAtoms.add(buildWithVersion(i));
		}
		syncServiceRunner.start();
		responsesSubject.onNext(newAtoms.build());

		verify(consumer, times(1)).accept(argThat(a -> a.getVertexMetadata().getStateVersion() == 1000));
	}

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