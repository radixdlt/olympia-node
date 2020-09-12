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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.LocalSyncServiceProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.LocalSyncServiceProcessor.SyncedCommandSender;
import java.util.Comparator;
import org.junit.Before;
import org.junit.Test;

public class LocalSyncServiceProcessorTest {
	private StateSyncNetwork stateSyncNetwork;
	private LocalSyncServiceProcessor syncServiceProcessor;
	private SyncedCommandSender syncedCommandSender;
	private SyncTimeoutScheduler syncTimeoutScheduler;
	private VerifiedLedgerHeaderAndProof currentHeader;
	private Comparator<VerifiedLedgerHeaderAndProof> headerComparator;

	@Before
	public void setUp() {
		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		this.syncedCommandSender = mock(SyncedCommandSender.class);
		this.syncTimeoutScheduler = mock(SyncTimeoutScheduler.class);
		this.currentHeader = mock(VerifiedLedgerHeaderAndProof.class);
		this.headerComparator = mock(Comparator.class);
		this.syncServiceProcessor = new LocalSyncServiceProcessor(
			stateSyncNetwork,
			syncedCommandSender,
			syncTimeoutScheduler,
			headerComparator,
			currentHeader,
			1
		);
	}

	@Test
	public void given_some_current_header__when_response_with_higher_header__then_should_send_sync() {
		VerifiedCommandsAndProof commands = mock(VerifiedCommandsAndProof.class);
		VerifiedLedgerHeaderAndProof ledgerHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(headerComparator.compare(ledgerHeader, currentHeader)).thenReturn(1);
		when(commands.getHeader()).thenReturn(ledgerHeader);
		syncServiceProcessor.processSyncResponse(commands);

		verify(syncedCommandSender, times(1)).sendSyncedCommand(eq(commands));
	}

	@Test
	public void given_some_current_header__when_response_with_lower_version__then_should_not_send_sync() {
		VerifiedCommandsAndProof commands = mock(VerifiedCommandsAndProof.class);
		VerifiedLedgerHeaderAndProof ledgerHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(headerComparator.compare(ledgerHeader, currentHeader)).thenReturn(-1);
		when(commands.getHeader()).thenReturn(ledgerHeader);
		syncServiceProcessor.processSyncResponse(commands);

		verify(syncedCommandSender, never()).sendSyncedCommand(eq(commands));
	}

	@Test
	public void given_some_current_header__when_local_request_has_equal_target__then_should_do_nothing() {
		VerifiedLedgerHeaderAndProof targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(headerComparator.compare(targetHeader, currentHeader)).thenReturn(0);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(targetHeader);
		syncServiceProcessor.processLocalSyncRequest(request);
		verify(syncedCommandSender, never()).sendSyncedCommand(any());
		verify(stateSyncNetwork, never()).sendSyncRequest(any(), any());
		verify(syncTimeoutScheduler, never()).scheduleTimeout(any(), anyLong());
	}

	@Test
	public void given_some_current_header__when_local_request_has_higher_target__then_should_send_timeout_and_remote_request() {
		when(currentHeader.getStateVersion()).thenReturn(1L);
		VerifiedLedgerHeaderAndProof targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(targetHeader.getStateVersion()).thenReturn(2L);
		when(headerComparator.compare(targetHeader, currentHeader)).thenReturn(1);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(targetHeader);
		when(request.getTargetNodes()).thenReturn(ImmutableList.of(mock(BFTNode.class)));
		syncServiceProcessor.processLocalSyncRequest(request);
		verify(syncedCommandSender, never()).sendSyncedCommand(any());
		verify(stateSyncNetwork, times(1)).sendSyncRequest(any(), any());
		verify(syncTimeoutScheduler, times(1)).scheduleTimeout(any(), anyLong());
	}

	// TODO: require state versions to define whether whether header is higher or lower
	// TODO: thus rendering this test deprecated
	@Test
	public void given_some_current_header__when_local_request_has_higher_target_but_same_version__then_should_send_timeout_and_remote_request() {
		when(currentHeader.getStateVersion()).thenReturn(1L);
		VerifiedLedgerHeaderAndProof targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(targetHeader.getStateVersion()).thenReturn(1L);
		when(headerComparator.compare(targetHeader, currentHeader)).thenReturn(1);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(targetHeader);
		when(request.getTargetNodes()).thenReturn(ImmutableList.of(mock(BFTNode.class)));
		syncServiceProcessor.processLocalSyncRequest(request);
		verify(syncedCommandSender, times(1)).sendSyncedCommand(any());
		verify(stateSyncNetwork, never()).sendSyncRequest(any(), any());
		verify(syncTimeoutScheduler, never()).scheduleTimeout(any(), anyLong());
	}
}