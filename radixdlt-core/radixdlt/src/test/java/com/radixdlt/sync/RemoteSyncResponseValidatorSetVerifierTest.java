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

import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.InvalidValidatorSetSender;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.VerifiedValidatorSetSender;
import org.junit.Before;
import org.junit.Test;

public class RemoteSyncResponseValidatorSetVerifierTest {
	private VerifiedValidatorSetSender verifiedSender;
	private InvalidValidatorSetSender invalidSender;
	private BFTValidatorSet validatorSet;
	private RemoteSyncResponseValidatorSetVerifier validatorSetVerifier;
	private DtoCommandsAndProof commandsAndProof;

	@Before
	public void setup() {
		this.verifiedSender = mock(VerifiedValidatorSetSender.class);
		this.invalidSender = mock(InvalidValidatorSetSender.class);
		this.validatorSet = mock(BFTValidatorSet.class);
		this.validatorSetVerifier = new RemoteSyncResponseValidatorSetVerifier(
			verifiedSender,
			invalidSender,
			validatorSet
		);

		commandsAndProof = mock(DtoCommandsAndProof.class);
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

		validatorSetVerifier.process(BFTNode.random(), commandsAndProof);

		verify(verifiedSender, times(1)).sendVerified(any());
		verify(invalidSender, never()).sendInvalid(any());
	}

	@Test
	public void when_process_bad_validator_set__then_sends_invalid() {
		ValidationState validationState = mock(ValidationState.class);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validationState.complete()).thenReturn(false);

		validatorSetVerifier.process(BFTNode.random(), commandsAndProof);

		verify(verifiedSender, never()).sendVerified(any());
		verify(invalidSender, times(1)).sendInvalid(any());
	}
}