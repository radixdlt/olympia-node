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

import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.TimestampedVoteData;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * Verifies the signatures in a sync response
 */
public final class RemoteSyncResponseSignaturesVerifier implements RemoteSyncResponseProcessor {
	public interface VerifiedSignaturesSender {
		void sendVerified(RemoteSyncResponse remoteSyncResponse);
	}

	public interface InvalidSignaturesSender {
		void sendInvalid(RemoteSyncResponse remoteSyncResponse);
	}

	private final VerifiedSignaturesSender verifiedSignaturesSender;
	private final InvalidSignaturesSender invalidSignaturesSender;
	private final Hasher hasher;
	private final HashVerifier hashVerifier;

	@Inject
	public RemoteSyncResponseSignaturesVerifier(
		VerifiedSignaturesSender verifiedSignaturesSender,
		InvalidSignaturesSender invalidSignaturesSender,
		Hasher hasher,
		HashVerifier hashVerifier
	) {
		this.verifiedSignaturesSender = Objects.requireNonNull(verifiedSignaturesSender);
		this.invalidSignaturesSender = Objects.requireNonNull(invalidSignaturesSender);
		this.hasher = Objects.requireNonNull(hasher);
		this.hashVerifier = Objects.requireNonNull(hashVerifier);
	}

	@Override
	public void processSyncResponse(RemoteSyncResponse syncResponse) {
		DtoCommandsAndProof commandsAndProof = syncResponse.getCommandsAndProof();
		DtoLedgerHeaderAndProof endHeader = commandsAndProof.getTail();

		// TODO: Figure out where this reconstruction should take place
		VoteData voteData = endHeader.toVoteData();
		Map<BFTNode, TimestampedECDSASignature> signatures = endHeader.getSignatures().getSignatures();
		for (Entry<BFTNode, TimestampedECDSASignature> nodeAndSignature : signatures.entrySet()) {
			BFTNode node = nodeAndSignature.getKey();
			TimestampedECDSASignature signature = nodeAndSignature.getValue();
			final TimestampedVoteData timestampedVoteData = new TimestampedVoteData(voteData, signature.timestamp());
			final HashCode voteDataHash = this.hasher.hash(timestampedVoteData);

			if (!hashVerifier.verify(node.getKey(), voteDataHash, signature.signature())) {
				invalidSignaturesSender.sendInvalid(syncResponse);
				return;
			}
		}

		verifiedSignaturesSender.sendVerified(syncResponse);
	}

}
