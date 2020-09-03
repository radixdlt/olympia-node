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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.LedgerState;
import com.radixdlt.consensus.VerifiedCommittedHeader;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.ledger.VerifiedCommittedCommands;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.store.berkeley.NextCommittedLimitReachedException;
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
	public void when_remote_sync_request__then_process_it() throws NextCommittedLimitReachedException {
		SyncRequest syncRequest = mock(SyncRequest.class);
		Peer peer = mock(Peer.class);
		when(syncRequest.getPeer()).thenReturn(peer);
		VerifiedCommittedCommands verifiedCommittedCommands = mock(VerifiedCommittedCommands.class);
		when(verifiedCommittedCommands.getCommands()).thenReturn(ImmutableList.of());
		when(stateComputer.getNextCommittedCommands(anyLong(), anyInt())).thenReturn(verifiedCommittedCommands);
		syncServiceProcessor.processSyncRequest(syncRequest);
		verify(stateSyncNetwork, times(1)).sendSyncResponse(eq(peer), any());
	}

	@Test
	public void basicSynchronization() {
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

		LedgerState currentState = mock(LedgerState.class);
		when(currentState.getStateVersion()).thenReturn(6L);

		syncServiceProcessor.processVersionUpdate(currentState);
		LocalSyncRequest request = new LocalSyncRequest(verifiedCommittedHeader, ImmutableList.of(node));
		syncServiceProcessor.processLocalSyncRequest(request);

		VerifiedCommittedCommands commands = mock(VerifiedCommittedCommands.class);
		VerifiedCommittedHeader proof = mock(VerifiedCommittedHeader.class);
		LedgerState proofLedgerState = mock(LedgerState.class);
		when(proofLedgerState.getStateVersion()).thenReturn(15L);
		when(proof.getLedgerState()).thenReturn(ledgerState);
		when(commands.getProof()).thenReturn(proof);
		syncServiceProcessor.processSyncResponse(commands);

		verify(syncedCommandSender, times(1)).sendSyncedCommand(eq(commands));
	}

	@Test
	public void requestSent() {
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

		LedgerState currentLedgerState = mock(LedgerState.class);
		when(currentLedgerState.getStateVersion()).thenReturn(10L);

		syncServiceProcessor.processVersionUpdate(currentLedgerState);
		LocalSyncRequest request = new LocalSyncRequest(verifiedCommittedHeader, ImmutableList.of(node));
		syncServiceProcessor.processLocalSyncRequest(request);
		verify(stateSyncNetwork, times(1)).sendSyncRequest(any(), eq(10L));
	}
}