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

import com.google.inject.Inject;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.ConsensusHasher;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.sync.messages.remote.SyncResponse;

import java.util.Map.Entry;
import java.util.Objects;

/**
 * Verifies the signatures in a sync response
 */
public final class RemoteSyncResponseSignaturesVerifier {

	private final Hasher hasher;
	private final HashVerifier hashVerifier;

	@Inject
	public RemoteSyncResponseSignaturesVerifier(Hasher hasher, HashVerifier hashVerifier) {
		this.hasher = Objects.requireNonNull(hasher);
		this.hashVerifier = Objects.requireNonNull(hashVerifier);
	}

	public boolean verifyResponseSignatures(SyncResponse syncResponse) {
		var commandsAndProof = syncResponse.getTxnsAndProof();
		var endHeader = commandsAndProof.getTail();

		var opaque = endHeader.getOpaque();
		var header = endHeader.getLedgerHeader();
		var signatures = endHeader.getSignatures().getSignatures();
		for (Entry<BFTNode, TimestampedECDSASignature> nodeAndSignature : signatures.entrySet()) {
			var node = nodeAndSignature.getKey();
			var signature = nodeAndSignature.getValue();
			final var voteDataHash = ConsensusHasher.toHash(opaque, header, signature.timestamp(), hasher);
			if (!hashVerifier.verify(node.getKey(), voteDataHash, signature.signature())) {
				return false;
			}
		}

		return true;
	}

}
