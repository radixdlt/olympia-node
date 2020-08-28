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

package com.radixdlt.middleware2.network;

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;

import org.radix.serialization.SerializeMessageObject;

public class ConsensusEventMessageSerializeTest extends SerializeMessageObject<ConsensusEventMessage> {
	public ConsensusEventMessageSerializeTest() {
		super(ConsensusEventMessage.class, ConsensusEventMessageSerializeTest::get);
	}

	private static ConsensusEventMessage get() {
		PreparedCommand preparedCommand = PreparedCommand.create(0, 0L, false);
		VertexMetadata vertexMetadata = new VertexMetadata(0, View.of(1), Hash.ZERO_HASH, preparedCommand);
		VertexMetadata parent = new VertexMetadata(0, View.of(0), Hash.ZERO_HASH, preparedCommand);
		VoteData voteData = new VoteData(vertexMetadata, parent, null);
		QuorumCertificate quorumCertificate = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		BFTNode author = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
		NewView testView = new NewView(author, View.of(1234567890L), quorumCertificate, quorumCertificate, null);
		return new ConsensusEventMessage(1234, testView);
	}
}