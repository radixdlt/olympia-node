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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.AccumulatorLocalSyncServiceProcessor.DtoCommandsAndProofVerifier;
import com.radixdlt.sync.AccumulatorLocalSyncServiceProcessor.DtoCommandsAndProofVerifierException;
import com.radixdlt.sync.AccumulatorRemoteSyncResponseVerifier.InvalidSyncedCommandsSender;
import com.radixdlt.sync.AccumulatorRemoteSyncResponseVerifier.VerifiedSyncedCommandsSender;
import org.junit.Before;
import org.junit.Test;

public class AccumulatorRemoteSyncResponseVerifierTest {
	private AccumulatorRemoteSyncResponseVerifier responseVerifier;
	private InvalidSyncedCommandsSender invalidSyncedCommandsSender;
	private VerifiedSyncedCommandsSender verifiedSyncedCommandsSender;
	private DtoCommandsAndProofVerifier verifier;

	@Before
	public void setup() {
		this.invalidSyncedCommandsSender = mock(InvalidSyncedCommandsSender.class);
		this.verifiedSyncedCommandsSender = mock(VerifiedSyncedCommandsSender.class);
		this.verifier = mock(DtoCommandsAndProofVerifier.class);
		this.responseVerifier = new AccumulatorRemoteSyncResponseVerifier(
			verifiedSyncedCommandsSender,
			invalidSyncedCommandsSender,
			verifier
		);
	}


	@Test
	public void when_process_response_with_bad_verification__then_invalid_commands_sent() throws DtoCommandsAndProofVerifierException {
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		RemoteSyncResponse response = mock(RemoteSyncResponse.class);
		when(response.getCommandsAndProof()).thenReturn(dtoCommandsAndProof);
		when(verifier.verify(any())).thenThrow(mock(DtoCommandsAndProofVerifierException.class));

		responseVerifier.processSyncResponse(response);

		verify(invalidSyncedCommandsSender, times(1)).sendInvalidCommands(eq(dtoCommandsAndProof));
		verify(verifiedSyncedCommandsSender, never()).sendVerifiedCommands(any());
	}

	@Test
	public void when_process_response_with_good_accumulator__then_committed_commands_sent() throws DtoCommandsAndProofVerifierException {
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		VerifiedCommandsAndProof verified = mock(VerifiedCommandsAndProof.class);
		when(verifier.verify(eq(dtoCommandsAndProof))).thenReturn(verified);

		RemoteSyncResponse response = mock(RemoteSyncResponse.class);
		when(response.getCommandsAndProof()).thenReturn(dtoCommandsAndProof);

		responseVerifier.processSyncResponse(response);

		verify(invalidSyncedCommandsSender, never()).sendInvalidCommands(any());
		verify(verifiedSyncedCommandsSender, times(1))
			.sendVerifiedCommands(eq(verified));
	}
}