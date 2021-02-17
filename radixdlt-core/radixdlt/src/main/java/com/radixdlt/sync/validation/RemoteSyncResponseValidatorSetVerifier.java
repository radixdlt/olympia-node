/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.sync.messages.remote.SyncResponse;

import java.util.Objects;

/**
 * Verifies the signature set of sync remote responses and checks
 * whether the signatures form a quorum based on a validatorSet.
 */
public class RemoteSyncResponseValidatorSetVerifier {

	private final BFTValidatorSet validatorSet;

	public RemoteSyncResponseValidatorSetVerifier(BFTValidatorSet validatorSet) {
		this.validatorSet = Objects.requireNonNull(validatorSet);
	}

	public boolean verifyValidatorSet(SyncResponse syncResponse) {
		final DtoCommandsAndProof dtoCommandsAndProof = syncResponse.getCommandsAndProof();
		final ValidationState validationState = validatorSet.newValidationState();

		dtoCommandsAndProof.getTail().getSignatures().getSignatures().forEach((node, signature) ->
			validationState.addSignature(node, signature.timestamp(), signature.signature())
		);

		return validationState.complete();
	}
}
