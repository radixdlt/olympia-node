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

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.ledger.DtoCommandsAndProof;
import java.util.Objects;

/**
 * Verifies the signature set of sync remote responses and checks
 * whether the signatures form a quorum based on a validatorSet.
 */
public final class RemoteSyncResponseValidatorSetVerifier implements RemoteEventProcessor<DtoCommandsAndProof> {
	public interface VerifiedValidatorSetSender {
		void sendVerified(RemoteSyncResponse remoteSyncResponse);
	}

	public interface InvalidValidatorSetSender {
		void sendInvalid(RemoteSyncResponse remoteSyncResponse);
	}

	private final VerifiedValidatorSetSender verifiedValidatorSetSender;
	private final InvalidValidatorSetSender invalidValidatorSetSender;
	private final BFTValidatorSet validatorSet;

	public RemoteSyncResponseValidatorSetVerifier(
		VerifiedValidatorSetSender verifiedValidatorSetSender,
		InvalidValidatorSetSender invalidValidatorSetSender,
		BFTValidatorSet validatorSet
	) {
		this.verifiedValidatorSetSender = Objects.requireNonNull(verifiedValidatorSetSender);
		this.invalidValidatorSetSender = Objects.requireNonNull(invalidValidatorSetSender);
		this.validatorSet = Objects.requireNonNull(validatorSet);
	}

	@Override
	public void process(BFTNode sender, DtoCommandsAndProof dtoCommandsAndProof) {
		ValidationState validationState = validatorSet.newValidationState();

		dtoCommandsAndProof.getTail().getSignatures().getSignatures().forEach((node, signature) ->
			validationState.addSignature(node, signature.timestamp(), signature.signature())
		);

		if (!validationState.complete()) {
			invalidValidatorSetSender.sendInvalid(new RemoteSyncResponse(sender, dtoCommandsAndProof));
			return;
		}

		verifiedValidatorSetSender.sendVerified(new RemoteSyncResponse(sender, dtoCommandsAndProof));
	}
}
