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

import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.messages.ProposalMessage;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;

public class ProposalMessageSerializeTest extends SerializeMessageObject<ProposalMessage> {
	public ProposalMessageSerializeTest() {
		super(ProposalMessage.class, ProposalMessageSerializeTest::get);
	}

	private static ProposalMessage get() {
		View view = View.of(1234567891L);
		Hash id = Hash.random();
		Atom atom = new Atom();

		VertexMetadata vertexMetadata = new VertexMetadata(view, id);
		VertexMetadata parent = new VertexMetadata(View.of(1234567890L), Hash.random());
		VoteData voteData = new VoteData(vertexMetadata, parent);
		QuorumCertificate qc = new QuorumCertificate(voteData, new ECDSASignatures());
		Vertex vertex = new Vertex(qc, view, atom);
		Proposal proposal = new Proposal(vertex, ECKeyPair.generateNew().getPublicKey(), new ECDSASignature());
		return new ProposalMessage(1, proposal);
	}
}
