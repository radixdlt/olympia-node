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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.sync.RemoteSyncResponseAccumulatorVerifier.InvalidAccumulatorSender;
import com.radixdlt.sync.RemoteSyncResponseAccumulatorVerifier.VerifiedAccumulatorSender;
import org.junit.Before;
import org.junit.Test;

public class RemoteSyncResponseAccumulatorVerifierTest {
	private RemoteSyncResponseAccumulatorVerifier responseVerifier;
	private InvalidAccumulatorSender invalidAccumulatorSender;
	private VerifiedAccumulatorSender verifiedAccumulatorSender;
	private LedgerAccumulatorVerifier ledgerAccumulatorVerifier;
	private Hasher hasher;

	@Before
	public void setup() {
		this.invalidAccumulatorSender = mock(InvalidAccumulatorSender.class);
		this.verifiedAccumulatorSender = mock(VerifiedAccumulatorSender.class);
		this.ledgerAccumulatorVerifier = mock(LedgerAccumulatorVerifier.class);
		this.hasher = mock(Hasher.class);
		when(hasher.hash(any())).thenReturn(mock(HashCode.class));
		this.responseVerifier = new RemoteSyncResponseAccumulatorVerifier(
			verifiedAccumulatorSender,
			invalidAccumulatorSender,
			ledgerAccumulatorVerifier,
			hasher
		);
	}

	@Test
	public void when_process_response_with_bad_verification__then_invalid_commands_sent() {
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		when(dtoCommandsAndProof.getCommands()).thenReturn(ImmutableList.of());
		DtoLedgerHeaderAndProof dtoLedgerHeaderAndProof = mock(DtoLedgerHeaderAndProof.class);
		when(dtoLedgerHeaderAndProof.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(dtoCommandsAndProof.getHead()).thenReturn(dtoLedgerHeaderAndProof);
		when(dtoCommandsAndProof.getTail()).thenReturn(dtoLedgerHeaderAndProof);
		RemoteSyncResponse response = mock(RemoteSyncResponse.class);
		when(response.getCommandsAndProof()).thenReturn(dtoCommandsAndProof);
		when(ledgerAccumulatorVerifier.verify(any(), any(), any())).thenReturn(false);

		responseVerifier.processSyncResponse(response);

		verify(invalidAccumulatorSender, times(1)).sendInvalidAccumulator(any());
		verify(verifiedAccumulatorSender, never()).sendVerifiedAccumulator(any());
	}

	@Test
	public void when_process_response_with_good_accumulator__then_committed_commands_sent() {
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		when(dtoCommandsAndProof.getCommands()).thenReturn(ImmutableList.of());
		DtoLedgerHeaderAndProof dtoLedgerHeaderAndProof = mock(DtoLedgerHeaderAndProof.class);
		when(dtoLedgerHeaderAndProof.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(dtoCommandsAndProof.getHead()).thenReturn(dtoLedgerHeaderAndProof);
		when(dtoCommandsAndProof.getTail()).thenReturn(dtoLedgerHeaderAndProof);
		when(ledgerAccumulatorVerifier.verify(any(), any(), any())).thenReturn(true);

		RemoteSyncResponse response = mock(RemoteSyncResponse.class);
		when(response.getCommandsAndProof()).thenReturn(dtoCommandsAndProof);
		responseVerifier.processSyncResponse(response);

		verify(invalidAccumulatorSender, never()).sendInvalidAccumulator(any());
		verify(verifiedAccumulatorSender, times(1)).sendVerifiedAccumulator(any());
	}
}