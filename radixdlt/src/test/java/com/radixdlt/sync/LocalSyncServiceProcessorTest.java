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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.LocalSyncServiceProcessor.DtoCommandsAndProofVerifier;
import com.radixdlt.sync.LocalSyncServiceProcessor.DtoCommandsAndProofVerifierException;
import com.radixdlt.sync.LocalSyncServiceProcessor.InvalidSyncedCommandsSender;
import com.radixdlt.sync.LocalSyncServiceProcessor.SyncInProgress;
import com.radixdlt.sync.LocalSyncServiceProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.LocalSyncServiceProcessor.VerifiedSyncedCommandsSender;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

public class LocalSyncServiceProcessorTest {
	private StateSyncNetwork stateSyncNetwork;
	private LocalSyncServiceProcessor syncServiceProcessor;
	private VerifiedSyncedCommandsSender verifiedSyncedCommandsSender;
	private InvalidSyncedCommandsSender invalidSyncedCommandsSender;
	private DtoCommandsAndProofVerifier verifier;
	private SyncTimeoutScheduler syncTimeoutScheduler;
	private VerifiedLedgerHeaderAndProof currentHeader;
	private Comparator<VerifiedLedgerHeaderAndProof> headerComparator;
	private AccumulatorState currentAccumulatorState;

	@Before
	public void setUp() {
		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		this.verifiedSyncedCommandsSender = mock(VerifiedSyncedCommandsSender.class);
		this.invalidSyncedCommandsSender = mock(InvalidSyncedCommandsSender.class);
		this.syncTimeoutScheduler = mock(SyncTimeoutScheduler.class);
		this.currentHeader = mock(VerifiedLedgerHeaderAndProof.class);
		this.currentAccumulatorState = mock(AccumulatorState.class);
		when(currentHeader.getAccumulatorState()).thenReturn(currentAccumulatorState);
		when(this.currentHeader.toDto()).thenReturn(mock(DtoLedgerHeaderAndProof.class));
		this.verifier = mock(DtoCommandsAndProofVerifier.class);
		this.headerComparator = mock(Comparator.class);
		this.syncServiceProcessor = new LocalSyncServiceProcessor(
			stateSyncNetwork,
			verifiedSyncedCommandsSender,
			invalidSyncedCommandsSender,
			syncTimeoutScheduler,
			verifier,
			headerComparator,
			currentHeader,
			1
		);
	}

	@Test
	public void when_process_response_with_bad_verification__then_invalid_commands_sent() throws DtoCommandsAndProofVerifierException {
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		when(verifier.verify(any())).thenThrow(mock(DtoCommandsAndProofVerifierException.class));

		syncServiceProcessor.processSyncResponse(dtoCommandsAndProof);

		verify(invalidSyncedCommandsSender, times(1)).sendInvalidCommands(eq(dtoCommandsAndProof));
		verify(verifiedSyncedCommandsSender, never()).sendVerifiedCommands(any());
	}

	@Test
	public void when_process_response_with_good_accumulator__then_committed_commands_sent() throws DtoCommandsAndProofVerifierException {
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		VerifiedCommandsAndProof verified = mock(VerifiedCommandsAndProof.class);
		when(verifier.verify(eq(dtoCommandsAndProof))).thenReturn(verified);

		syncServiceProcessor.processSyncResponse(dtoCommandsAndProof);
		verify(invalidSyncedCommandsSender, never()).sendInvalidCommands(any());
		verify(verifiedSyncedCommandsSender, times(1))
			.sendVerifiedCommands(eq(verified));
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
		when(targetHeader.getAccumulatorState()).thenReturn(currentAccumulatorState);
		when(headerComparator.compare(targetHeader, currentHeader)).thenReturn(1);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(targetHeader);
		when(request.getTargetNodes()).thenReturn(ImmutableList.of(mock(BFTNode.class)));
		syncServiceProcessor.processLocalSyncRequest(request);

		verify(verifiedSyncedCommandsSender, times(1)).sendVerifiedCommands(any());
		verify(stateSyncNetwork, never()).sendSyncRequest(any(), any());
		verify(syncTimeoutScheduler, never()).scheduleTimeout(any(), anyLong());
	}

	@Test
	public void given_some_sync_in_progress_which_has_been_fulfilled__when_sync_timeout__then_should_do_nothing() {
		AtomicReference<SyncInProgress> sync = new AtomicReference<>();
		doAnswer(invocation -> {
			sync.set(invocation.getArgument(0));
			return null;
		}).when(syncTimeoutScheduler).scheduleTimeout(any(), anyLong());
		VerifiedLedgerHeaderAndProof targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(targetHeader.getStateVersion()).thenReturn(2L);
		when(headerComparator.compare(targetHeader, currentHeader)).thenReturn(1);
		LocalSyncRequest request = mock(LocalSyncRequest.class);
		when(request.getTarget()).thenReturn(targetHeader);
		when(request.getTargetNodes()).thenReturn(ImmutableList.of(mock(BFTNode.class)));
		LedgerUpdate ledgerUpdate = mock(LedgerUpdate.class);

		when(ledgerUpdate.getTail()).thenReturn(targetHeader);
		syncServiceProcessor.processLocalSyncRequest(request);
		syncServiceProcessor.processLedgerUpdate(ledgerUpdate);
		syncServiceProcessor.processSyncTimeout(sync.get());

		// Once only for initial setup
		verify(stateSyncNetwork, times(1)).sendSyncRequest(any(), any());
		verify(syncTimeoutScheduler, times(1)).scheduleTimeout(any(), anyLong());
		verify(verifiedSyncedCommandsSender, never()).sendVerifiedCommands(any());
	}
}