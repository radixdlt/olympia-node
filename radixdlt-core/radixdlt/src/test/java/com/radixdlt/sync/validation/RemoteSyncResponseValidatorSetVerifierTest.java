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

package com.radixdlt.sync.validation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.ledger.DtoTxnsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.sync.messages.remote.SyncResponse;
import org.junit.Before;
import org.junit.Test;

public class RemoteSyncResponseValidatorSetVerifierTest {
	private BFTValidatorSet validatorSet;
	private RemoteSyncResponseValidatorSetVerifier validatorSetVerifier;
	private DtoTxnsAndProof commandsAndProof;

	@Before
	public void setup() {
		this.validatorSet = mock(BFTValidatorSet.class);
		this.validatorSetVerifier = new RemoteSyncResponseValidatorSetVerifier(validatorSet);
		commandsAndProof = mock(DtoTxnsAndProof.class);
		DtoLedgerHeaderAndProof headerAndProof = mock(DtoLedgerHeaderAndProof.class);
		TimestampedECDSASignatures signatures = mock(TimestampedECDSASignatures.class);
		when(signatures.getSignatures()).thenReturn(ImmutableMap.of());
		when(headerAndProof.getSignatures()).thenReturn(signatures);
		when(commandsAndProof.getTail()).thenReturn(headerAndProof);
	}

	@Test
	public void when_process_good_validator_set__then_sends_verified() {
		ValidationState validationState = mock(ValidationState.class);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validationState.complete()).thenReturn(true);

		assertTrue(validatorSetVerifier.verifyValidatorSet(SyncResponse.create(commandsAndProof)));
	}

	@Test
	public void when_process_bad_validator_set__then_sends_invalid() {
		ValidationState validationState = mock(ValidationState.class);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validationState.complete()).thenReturn(false);

		assertFalse(validatorSetVerifier.verifyValidatorSet(SyncResponse.create(commandsAndProof)));
	}
}
