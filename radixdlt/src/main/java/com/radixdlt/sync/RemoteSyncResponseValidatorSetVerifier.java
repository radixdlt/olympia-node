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

import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.TimestampedVoteData;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import java.util.Map;
import java.util.Objects;

public class RemoteSyncResponseValidatorSetVerifier implements RemoteSyncResponseProcessor {

	public interface VerifiedValidatorSetSender {
		void sendVerified(RemoteSyncResponse remoteSyncResponse);
	}

	public interface InvalidValidatorSetSender {
		void sendInvalid(RemoteSyncResponse remoteSyncResponse, String message);
	}

	private final VerifiedValidatorSetSender verifiedValidatorSetSender;
	private final InvalidValidatorSetSender invalidValidatorSetSender;
	private final BFTValidatorSet validatorSet;
	private final Hasher hasher;
	private final HashVerifier hashVerifier;

	public RemoteSyncResponseValidatorSetVerifier(
		VerifiedValidatorSetSender verifiedValidatorSetSender,
		InvalidValidatorSetSender invalidValidatorSetSender,
		BFTValidatorSet validatorSet,
		Hasher hasher,
		HashVerifier hashVerifier
	) {
		this.verifiedValidatorSetSender = Objects.requireNonNull(verifiedValidatorSetSender);
		this.invalidValidatorSetSender = Objects.requireNonNull(invalidValidatorSetSender);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.hasher = Objects.requireNonNull(hasher);
		this.hashVerifier = Objects.requireNonNull(hashVerifier);
	}

	@Override
	public void processSyncResponse(RemoteSyncResponse syncResponse) {
		ValidationState validationState = validatorSet.newValidationState();
		DtoCommandsAndProof commandsAndProof = syncResponse.getCommandsAndProof();

		commandsAndProof.getTail().getSignatures().getSignatures().forEach((node, signature) ->
			validationState.addSignature(node, signature.timestamp(), signature.signature())
		);

		if (!validationState.complete()) {
			invalidValidatorSetSender.sendInvalid(syncResponse, "Invalid signature count");
			return;
		}

		DtoLedgerHeaderAndProof endHeader = commandsAndProof.getTail();
		VoteData voteData = new VoteData(
			endHeader.getOpaque0(),
			endHeader.getOpaque1(),
			new BFTHeader(
				View.of(endHeader.getOpaque2()),
				endHeader.getOpaque3(),
				endHeader.getLedgerHeader()
			)
		);
		Map<BFTNode, TimestampedECDSASignature> signatures = endHeader.getSignatures().getSignatures();
		for (BFTNode node : signatures.keySet()) {
			TimestampedECDSASignature signature = signatures.get(node);
			final TimestampedVoteData timestampedVoteData = new TimestampedVoteData(voteData, signature.timestamp());
			final Hash voteDataHash = this.hasher.hash(timestampedVoteData);

			if (!hashVerifier.verify(node.getKey(), voteDataHash, signature.signature())) {
				invalidValidatorSetSender.sendInvalid(syncResponse, "Invalid signature");
				return;
			}
		}

		verifiedValidatorSetSender.sendVerified(syncResponse);


	}
}
