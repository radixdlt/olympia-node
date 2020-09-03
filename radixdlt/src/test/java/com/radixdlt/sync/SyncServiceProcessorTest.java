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

package com.radixdlt.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.LedgerState;
import com.radixdlt.consensus.VerifiedCommittedHeader;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.ledger.VerifiedCommittedCommand;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.sync.SyncServiceProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.SyncServiceProcessor.SyncedCommandSender;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class SyncServiceProcessorTest {

	private StateSyncNetwork stateSyncNetwork;
	private SyncServiceProcessor syncServiceProcessor;
	private AddressBook addressBook;
	private RadixEngineStateComputer stateComputer;
	private SyncedCommandSender syncedCommandSender;
	private SyncTimeoutScheduler syncTimeoutScheduler;

	private static VerifiedCommittedCommand buildWithVersion(long version) {
		VerifiedCommittedCommand committedCommand = mock(VerifiedCommittedCommand.class);
		VerifiedCommittedHeader proof = mock(VerifiedCommittedHeader.class);
		LedgerState ledgerState = mock(LedgerState.class);
		when(ledgerState.getStateVersion()).thenReturn(version);
		when(proof.getLedgerState()).thenReturn(ledgerState);
		when(committedCommand.getProof()).thenReturn(proof);
		return committedCommand;
	}

	@Before
	public void setUp() {

		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		this.addressBook = mock(AddressBook.class);
		this.stateComputer = mock(RadixEngineStateComputer.class);
		this.syncedCommandSender = mock(SyncedCommandSender.class);
		this.syncTimeoutScheduler = mock(SyncTimeoutScheduler.class);
		this.syncServiceProcessor = new SyncServiceProcessor(
			stateComputer,
			stateSyncNetwork,
			addressBook,
			syncedCommandSender,
			syncTimeoutScheduler,
			VerifiedCommittedHeader.ofGenesisAncestor(mock(LedgerState.class)),
			2,
			1
		);
	}

	@Test
	public void when_remote_sync_request__then_process_it() {
		SyncRequest syncRequest = mock(SyncRequest.class);
		Peer peer = mock(Peer.class);
		when(syncRequest.getPeer()).thenReturn(peer);
		when(stateComputer.getCommittedCommands(anyLong(), anyInt())).thenReturn(ImmutableList.of());
		syncServiceProcessor.processSyncRequest(syncRequest);
		verify(stateSyncNetwork, times(1)).sendSyncResponse(eq(peer), any());
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

		VerifiedCommittedHeader verifiedCommittedHeader = mock(VerifiedCommittedHeader.class);
		LedgerState ledgerState = mock(LedgerState.class);
		when(ledgerState.getStateVersion()).thenReturn(targetVersion);
		when(verifiedCommittedHeader.getLedgerState()).thenReturn(ledgerState);

		syncServiceProcessor.processVersionUpdate(currentVersion);
		LocalSyncRequest request = new LocalSyncRequest(verifiedCommittedHeader, ImmutableList.of(node));
		syncServiceProcessor.processLocalSyncRequest(request);

		ImmutableList.Builder<VerifiedCommittedCommand> newCommands1 = ImmutableList.builder();
		for (int i = 7; i <= 12; i++) {
			newCommands1.add(buildWithVersion(i));
		}
		syncServiceProcessor.processSyncResponse(newCommands1.build());
		ImmutableList.Builder<VerifiedCommittedCommand> newAtoms2 = ImmutableList.builder();
		for (int i = 10; i <= 18; i++) {
			newAtoms2.add(buildWithVersion(i));
		}
		syncServiceProcessor.processSyncResponse(newAtoms2.build());

		verify(syncedCommandSender, times((int) (18 - currentVersion))).sendSyncedCommand(any());
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

		VerifiedCommittedHeader verifiedCommittedHeader = mock(VerifiedCommittedHeader.class);
		LedgerState ledgerState = mock(LedgerState.class);
		when(ledgerState.getStateVersion()).thenReturn(targetVersion);
		when(verifiedCommittedHeader.getLedgerState()).thenReturn(ledgerState);

		syncServiceProcessor.processVersionUpdate(currentVersion);
		LocalSyncRequest request = new LocalSyncRequest(verifiedCommittedHeader, ImmutableList.of(node));
		syncServiceProcessor.processLocalSyncRequest(request);
		ImmutableList.Builder<VerifiedCommittedCommand> newCommands1 = ImmutableList.builder();
		for (int i = 7; i <= 11; i++) {
			newCommands1.add(buildWithVersion(i));
		}
		newCommands1.add(buildWithVersion(13));
		newCommands1.add(buildWithVersion(15));
		syncServiceProcessor.processSyncResponse(newCommands1.build());
		verify(syncedCommandSender, never())
			.sendSyncedCommand(argThat(a -> a.getProof().getLedgerState().getStateVersion() > 11));
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

		VerifiedCommittedHeader verifiedCommittedHeader = mock(VerifiedCommittedHeader.class);
		LedgerState ledgerState = mock(LedgerState.class);
		when(ledgerState.getStateVersion()).thenReturn(targetVersion);
		when(verifiedCommittedHeader.getLedgerState()).thenReturn(ledgerState);

		syncServiceProcessor.processVersionUpdate(currentVersion);
		LocalSyncRequest request = new LocalSyncRequest(verifiedCommittedHeader, ImmutableList.of(node));
		syncServiceProcessor.processLocalSyncRequest(request);
		verify(stateSyncNetwork, times(1)).sendSyncRequest(any(), eq(10L));
	}

	@Test
	public void atomsListPruning() {
		ImmutableList.Builder<VerifiedCommittedCommand> newCommands = ImmutableList.builder();
		for (int i = 1000; i >= 1; i--) {
			newCommands.add(buildWithVersion(i));
		}
		syncServiceProcessor.processSyncResponse(newCommands.build());

		verify(syncedCommandSender, times(1))
			.sendSyncedCommand(argThat(a -> a.getProof().getLedgerState().getStateVersion() == 1));
	}
}