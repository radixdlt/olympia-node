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

import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.messages.VertexMessage;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.Hash;

public class VertexMessageSerializeTest extends SerializeMessageObject<VertexMessage> {
	public VertexMessageSerializeTest() {
		super(VertexMessage.class, VertexMessageSerializeTest::get);
	}

	private static VertexMessage get() {
		View parentView = View.of(1234567890L);
		View view = parentView.next();
		Hash parentId = Hash.random();
		Hash id = Hash.random();
		EUID author = EUID.TWO;
		Atom atom = new Atom();

		VertexMetadata vertexMetadata = new VertexMetadata(view, id, parentView, parentId);
		QuorumCertificate qc = new QuorumCertificate(vertexMetadata, new ECDSASignatures());
		Vertex vertex = new Vertex(qc, view, atom);
		return new VertexMessage(1, vertex);
	}
}
