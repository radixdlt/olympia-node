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

package com.radixdlt.epochs;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.RemoteSyncResponse;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier;

import java.util.Optional;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;

public class EpochsRemoteSyncResponseProcessorTest {
	private EpochsRemoteSyncResponseProcessor responseProcessor;
	private EventProcessor<LocalSyncRequest> localSyncRequestSender;
	private RemoteSyncResponseValidatorSetVerifier initialVerifier;
	private EpochChange initialEpoch;
	private VerifiedLedgerHeaderAndProof currentHeader;
	private Function<BFTConfiguration, RemoteSyncResponseValidatorSetVerifier> verifierFactory;

	@Before
	public void setup() {
		this.initialVerifier = mock(RemoteSyncResponseValidatorSetVerifier.class);
		this.initialEpoch = mock(EpochChange.class);
		this.currentHeader = mock(VerifiedLedgerHeaderAndProof.class);

		this.localSyncRequestSender = rmock(EventProcessor.class);
		this.verifierFactory = rmock(Function.class);

		this.responseProcessor = new EpochsRemoteSyncResponseProcessor(
			localSyncRequestSender,
			initialVerifier,
			initialEpoch,
			currentHeader,
			verifierFactory
		);
	}

	@Test
	public void when_process_ledger_update_with_no_epoch_change__then_should_do_nothing() {
		EpochsLedgerUpdate update = mock(EpochsLedgerUpdate.class);
		when(update.getEpochChange()).thenReturn(Optional.empty());
		this.responseProcessor.processLedgerUpdate(update);

		verify(localSyncRequestSender, never()).processEvent(any());
		verify(initialVerifier, never()).processSyncResponse(any());
		verify(verifierFactory, never()).apply(any());
	}

	@Test
	public void given_epoch_is_1__when_process_some_response_with_epoch_2__then_should_do_nothing() {
		when(initialEpoch.getEpoch()).thenReturn(1L);

		RemoteSyncResponse response = mock(RemoteSyncResponse.class);
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		DtoLedgerHeaderAndProof headerAndProof = mock(DtoLedgerHeaderAndProof.class);
		LedgerHeader ledgerHeader = mock(LedgerHeader.class);
		when(ledgerHeader.getEpoch()).thenReturn(2L);
		when(headerAndProof.getLedgerHeader()).thenReturn(ledgerHeader);
		when(dtoCommandsAndProof.getTail()).thenReturn(headerAndProof);
		when(response.getCommandsAndProof()).thenReturn(dtoCommandsAndProof);
		this.responseProcessor.processSyncResponse(response);

		verify(localSyncRequestSender, never()).processEvent(any());
		verify(initialVerifier, never()).processSyncResponse(any());
		verify(verifierFactory, never()).apply(any());
	}


	@Test
	public void given_epoch_is_1__when_process_epoch_change_and_some_normal_response_with_epoch_2__then_should_go_through_verification() {
		when(initialEpoch.getEpoch()).thenReturn(1L);
		RemoteSyncResponseValidatorSetVerifier nextValidatorSetVerifier = mock(RemoteSyncResponseValidatorSetVerifier.class);
		when(verifierFactory.apply(any())).thenReturn(nextValidatorSetVerifier);

		EpochsLedgerUpdate update = mock(EpochsLedgerUpdate.class);
		EpochChange epochChange = mock(EpochChange.class);
		when(epochChange.getEpoch()).thenReturn(2L);
		when(epochChange.getProof()).thenReturn(mock(VerifiedLedgerHeaderAndProof.class));
		when(epochChange.getBFTConfiguration()).thenReturn(mock(BFTConfiguration.class));
		when(update.getEpochChange()).thenReturn(Optional.of(epochChange));
		this.responseProcessor.processLedgerUpdate(update);
		RemoteSyncResponse response = mock(RemoteSyncResponse.class);
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		DtoLedgerHeaderAndProof tail = mock(DtoLedgerHeaderAndProof.class);
		LedgerHeader ledgerHeader = mock(LedgerHeader.class);
		when(ledgerHeader.getEpoch()).thenReturn(2L);
		when(tail.getLedgerHeader()).thenReturn(ledgerHeader);
		DtoLedgerHeaderAndProof head = mock(DtoLedgerHeaderAndProof.class);
		when(head.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(dtoCommandsAndProof.getHead()).thenReturn(head);
		when(dtoCommandsAndProof.getTail()).thenReturn(tail);
		when(response.getCommandsAndProof()).thenReturn(dtoCommandsAndProof);
		this.responseProcessor.processSyncResponse(response);

		verify(localSyncRequestSender, never()).processEvent(any());
		verify(initialVerifier, never()).processSyncResponse(any());
		verify(nextValidatorSetVerifier, times(1)).processSyncResponse(any());
	}

	@Test
	public void given_epoch_is_1__when_process_epoch_sync_response__then_should_send_local_sync_request() {
		LedgerHeader headHeader = mock(LedgerHeader.class);
		when(headHeader.getEpoch()).thenReturn(0L);
		when(initialEpoch.getEpoch()).thenReturn(1L);
		VerifiedLedgerHeaderAndProof verifiedLedgerHeaderAndProof = mock(VerifiedLedgerHeaderAndProof.class);
		when(verifiedLedgerHeaderAndProof.getRaw()).thenReturn(headHeader);
		when(initialEpoch.getProof()).thenReturn(verifiedLedgerHeaderAndProof);

		RemoteSyncResponse response = mock(RemoteSyncResponse.class);
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		DtoLedgerHeaderAndProof tail = mock(DtoLedgerHeaderAndProof.class);
		LedgerHeader tailHeader = mock(LedgerHeader.class);
		when(tailHeader.getEpoch()).thenReturn(1L);
		when(tailHeader.isEndOfEpoch()).thenReturn(true);
		when(tailHeader.getAccumulatorState()).thenReturn(mock(AccumulatorState.class));
		when(tail.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(tail.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(tail.getOpaque3()).thenReturn(mock(HashCode.class));
		when(tail.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(tail.getLedgerHeader()).thenReturn(tailHeader);
		when(dtoCommandsAndProof.getTail()).thenReturn(tail);
		DtoLedgerHeaderAndProof head = mock(DtoLedgerHeaderAndProof.class);
		when(head.getLedgerHeader()).thenReturn(headHeader);
		when(dtoCommandsAndProof.getHead()).thenReturn(head);
		when(dtoCommandsAndProof.getTail()).thenReturn(tail);
		when(response.getCommandsAndProof()).thenReturn(dtoCommandsAndProof);
		when(response.getSender()).thenReturn(mock(BFTNode.class));
		this.responseProcessor.processSyncResponse(response);

		verify(localSyncRequestSender, times(1)).processEvent(any());
		verify(initialVerifier, never()).processSyncResponse(any());
		verify(verifierFactory, never()).apply(any());
	}
}