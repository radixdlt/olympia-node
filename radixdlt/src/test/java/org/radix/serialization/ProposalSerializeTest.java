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

package org.radix.serialization;

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;

public class ProposalSerializeTest extends SerializeObject<Proposal> {
	public ProposalSerializeTest() {
		super(Proposal.class, ProposalSerializeTest::get);
	}

	private static Proposal get() {
		View view = View.of(1234567891L);
		Hash id = Hash.random();

		PreparedCommand preparedCommand = PreparedCommand.create(0, 0L, false);
		VertexMetadata vertexMetadata = new VertexMetadata(0, view, id, preparedCommand);
		VertexMetadata parent = new VertexMetadata(0, View.of(1234567890L), Hash.random(), preparedCommand);
		VoteData voteData = new VoteData(vertexMetadata, parent, null);
		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		Command command = new Command(new byte[] {0, 1, 2, 3});

		// add a particle to ensure atom is valid and has at least one shard
		Vertex vertex = Vertex.createVertex(qc, view, command);
		BFTNode author = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
		return new Proposal(vertex, qc, author, new ECDSASignature(), 123456L);
	}
}
