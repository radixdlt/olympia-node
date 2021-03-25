/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.serialization;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.TimestampedVoteData;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.ledger.AccumulatorState;

import java.util.Optional;

public class VoteSerializeTest extends SerializeObject<Vote> {
	public VoteSerializeTest() {
		super(Vote.class, VoteSerializeTest::get);
	}

	private static Vote get() {
		View view = View.of(1234567891L);
		HashCode id = HashUtils.random256();

		LedgerHeader ledgerHeader = LedgerHeader.genesis(new AccumulatorState(0,  HashUtils.zero256()), null);
		BFTHeader header = new BFTHeader(view, id, ledgerHeader);
		BFTHeader parent = new BFTHeader(View.of(1234567890L), HashUtils.random256(), ledgerHeader);
		VoteData voteData = new VoteData(header, parent, null);
		TimestampedVoteData timestampedVoteData = new TimestampedVoteData(voteData, 123456L);
		BFTNode author = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		HighQC highQC = HighQC.from(qc, qc, Optional.empty());
		return new Vote(author, timestampedVoteData, new ECDSASignature(), highQC, Optional.empty());
	}
}
