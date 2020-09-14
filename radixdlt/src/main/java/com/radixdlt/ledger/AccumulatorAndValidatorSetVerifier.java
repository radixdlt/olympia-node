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

package com.radixdlt.ledger;

import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.crypto.Hash;
import com.radixdlt.sync.LocalSyncServiceProcessor.DtoCommandsAndProofVerifier;
import com.radixdlt.sync.LocalSyncServiceProcessor.DtoCommandsAndProofVerifierException;
import java.util.Objects;

/**
 * Verifies the accumulator and validator set signatures
 */
public final class AccumulatorAndValidatorSetVerifier implements DtoCommandsAndProofVerifier {
	private final LedgerAccumulatorVerifier accumulatorVerifier;
	private final BFTValidatorSet validatorSet;

	public AccumulatorAndValidatorSetVerifier(
		LedgerAccumulatorVerifier accumulatorVerifier,
		BFTValidatorSet validatorSet
	) {
		this.accumulatorVerifier = Objects.requireNonNull(accumulatorVerifier);
		this.validatorSet = Objects.requireNonNull(validatorSet);
	}

	@Override
	public VerifiedCommandsAndProof verify(DtoCommandsAndProof commandsAndProof) throws DtoCommandsAndProofVerifierException {
		Hash start = commandsAndProof.getStartHeader().getLedgerHeader().getAccumulator();
		Hash end = commandsAndProof.getEndHeader().getLedgerHeader().getAccumulator();
		if (!this.accumulatorVerifier.verify(start, commandsAndProof.getCommands(), end)) {
			throw new DtoCommandsAndProofVerifierException(commandsAndProof, "Bad commands");
		}

		ValidationState validationState = validatorSet.newValidationState();
		commandsAndProof.getEndHeader().getSignatures().getSignatures().forEach((node, signature) ->
			validationState.addSignature(node, signature.timestamp(), signature.signature())
		);
		if (!validationState.complete()) {
			throw new DtoCommandsAndProofVerifierException(commandsAndProof, "Invalid signature count");
		}

		// TODO: Stateful ledger header verification:
		// TODO: -verify rootHash matches
		// TODO: verify signatures

		VerifiedLedgerHeaderAndProof nextHeader = new VerifiedLedgerHeaderAndProof(
			commandsAndProof.getEndHeader().getOpaque0(),
			commandsAndProof.getEndHeader().getOpaque1(),
			commandsAndProof.getEndHeader().getOpaque2(),
			commandsAndProof.getEndHeader().getOpaque3(),
			commandsAndProof.getEndHeader().getLedgerHeader(),
			commandsAndProof.getEndHeader().getSignatures()
		);

		return new VerifiedCommandsAndProof(
			commandsAndProof.getCommands(),
			nextHeader
		);
	}
}
