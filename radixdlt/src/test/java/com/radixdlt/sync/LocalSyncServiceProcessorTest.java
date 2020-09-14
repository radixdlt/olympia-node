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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.sync.LocalSyncServiceProcessor.InvalidSyncedCommandsSender;
import com.radixdlt.sync.LocalSyncServiceProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.LocalSyncServiceProcessor.VerifiedSyncedCommandsSender;
import java.util.Comparator;
import org.junit.Before;
import org.junit.Test;

public class LocalSyncServiceProcessorTest {
	private StateSyncNetwork stateSyncNetwork;
	private LocalSyncServiceProcessor syncServiceProcessor;
	private VerifiedSyncedCommandsSender verifiedSyncedCommandsSender;
	private InvalidSyncedCommandsSender invalidSyncedCommandsSender;
	private LedgerAccumulator accumulator;
	private SyncTimeoutScheduler syncTimeoutScheduler;
	private VerifiedLedgerHeaderAndProof currentHeader;
	private Comparator<VerifiedLedgerHeaderAndProof> headerComparator;

	@Before
	public void setUp() {
		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		this.verifiedSyncedCommandsSender = mock(VerifiedSyncedCommandsSender.class);
		this.invalidSyncedCommandsSender = mock(InvalidSyncedCommandsSender.class);
		this.syncTimeoutScheduler = mock(SyncTimeoutScheduler.class);
		this.currentHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(this.currentHeader.toDto()).thenReturn(mock(DtoLedgerHeaderAndProof.class));
		this.accumulator = mock(LedgerAccumulator.class);
		this.headerComparator = mock(Comparator.class);
		this.syncServiceProcessor = new LocalSyncServiceProcessor(
			stateSyncNetwork,
			verifiedSyncedCommandsSender,
			invalidSyncedCommandsSender,
			syncTimeoutScheduler,
			accumulator,
			headerComparator,
			currentHeader,
			1
		);
	}


	@Test
	public void when_process_response_with_bad_accumulator__then_invalid_commands_sent() {
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		DtoLedgerHeaderAndProof start = mock(DtoLedgerHeaderAndProof.class);
		LedgerHeader ledgerHeader = mock(LedgerHeader.class);
		when(ledgerHeader.getAccumulator()).thenReturn(mock(Hash.class));
		when(start.getLedgerHeader()).thenReturn(ledgerHeader);

		DtoLedgerHeaderAndProof end = mock(DtoLedgerHeaderAndProof.class);
		LedgerHeader endHeader = mock(LedgerHeader.class);
		when(endHeader.getAccumulator()).thenReturn(mock(Hash.class));
		when(end.getLedgerHeader()).thenReturn(endHeader);

		when(dtoCommandsAndProof.getStartHeader()).thenReturn(start);
		when(dtoCommandsAndProof.getEndHeader()).thenReturn(end);

		Command command = mock(Command.class);
		when(command.getHash()).thenReturn(mock(Hash.class));
		when(dtoCommandsAndProof.getCommands()).thenReturn(ImmutableList.of(command));

		when(accumulator.verify(any(), any(), any())).thenReturn(false);

		syncServiceProcessor.processSyncResponse(dtoCommandsAndProof);

		verify(invalidSyncedCommandsSender, times(1)).sendInvalidCommands(eq(dtoCommandsAndProof));
		verify(verifiedSyncedCommandsSender, never()).sendVerifiedCommands(any());
	}

	@Test
	public void when_process_response_with_good_accumulator__then_committed_commands_sent() {
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		DtoLedgerHeaderAndProof start = mock(DtoLedgerHeaderAndProof.class);
		LedgerHeader ledgerHeader = mock(LedgerHeader.class);
		when(ledgerHeader.getAccumulator()).thenReturn(mock(Hash.class));
		when(start.getLedgerHeader()).thenReturn(ledgerHeader);

		DtoLedgerHeaderAndProof end = mock(DtoLedgerHeaderAndProof.class);
		LedgerHeader endHeader = mock(LedgerHeader.class);
		when(endHeader.getAccumulator()).thenReturn(mock(Hash.class));
		when(end.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(end.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(end.getOpaque3()).thenReturn(mock(Hash.class));
		when(end.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(end.getLedgerHeader()).thenReturn(endHeader);

		when(dtoCommandsAndProof.getStartHeader()).thenReturn(start);
		when(dtoCommandsAndProof.getEndHeader()).thenReturn(end);

		Command command = mock(Command.class);
		when(command.getHash()).thenReturn(mock(Hash.class));
		when(dtoCommandsAndProof.getCommands()).thenReturn(ImmutableList.of(command));

		when(accumulator.verify(any(), any(), any())).thenReturn(true);

		syncServiceProcessor.processSyncResponse(dtoCommandsAndProof);
		verify(invalidSyncedCommandsSender, never()).sendInvalidCommands(any());
		verify(verifiedSyncedCommandsSender, times(1))
			.sendVerifiedCommands(argThat(cmds -> cmds.contains(command)));
	}

	@Test
	public void given_some_current_header__when_local_request_has_equal_target__then_should_do_nothing() {
		VerifiedLedgerHeaderAndProof targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(headerComparator.compare(targetHeader, currentHeader)).thenReturn(0);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(targetHeader);
		syncServiceProcessor.processLocalSyncRequest(request);
		verify(verifiedSyncedCommandsSender, never()).sendVerifiedCommands(any());
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
		verify(verifiedSyncedCommandsSender, never()).sendVerifiedCommands(any());
		verify(stateSyncNetwork, times(1)).sendSyncRequest(any(), any());
		verify(syncTimeoutScheduler, times(1)).scheduleTimeout(any(), anyLong());
	}

	// TODO: require state versions to define whether whether header is higher or lower
	// TODO: thus rendering this test deprecated
	@Test
	public void given_some_current_header__when_local_request_has_higher_target_but_same_version__then_should_send_timeout_and_remote_request() {
		when(currentHeader.getStateVersion()).thenReturn(1L);
		VerifiedLedgerHeaderAndProof targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(targetHeader.toDto()).thenReturn(mock(DtoLedgerHeaderAndProof.class));
		when(targetHeader.getStateVersion()).thenReturn(1L);
		when(headerComparator.compare(targetHeader, currentHeader)).thenReturn(1);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(targetHeader);
		when(request.getTargetNodes()).thenReturn(ImmutableList.of(mock(BFTNode.class)));
		syncServiceProcessor.processLocalSyncRequest(request);
		verify(verifiedSyncedCommandsSender, times(1)).sendVerifiedCommands(any());
		verify(stateSyncNetwork, never()).sendSyncRequest(any(), any());
		verify(syncTimeoutScheduler, never()).scheduleTimeout(any(), anyLong());
	}
}