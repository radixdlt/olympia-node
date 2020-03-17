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
import com.radixdlt.consensus.Round;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.Vote;

public class VertexSerializeTest extends SerializeObject<Vertex> {
	public VertexSerializeTest() {
		super(Vertex.class, VertexSerializeTest::get);
	}

	private static Vertex get() {
		Round parentRound = Round.of(1234567890L);
		Round round = parentRound.next();
		Integer parentId = 12345;
		Integer id = 23456;

		VertexMetadata vertexMetadata = new VertexMetadata(round, id, parentRound, parentId);

		EUID author = EUID.TWO;
		Vote vote = new Vote(author, vertexMetadata);
		QuorumCertificate qc = new QuorumCertificate(vote, vertexMetadata);

		Atom atom = new Atom();

		return Vertex.createVertex(qc, round, atom);
	}
}
