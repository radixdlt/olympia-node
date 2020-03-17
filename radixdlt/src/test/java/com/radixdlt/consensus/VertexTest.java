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

package com.radixdlt.consensus;

import org.junit.Before;
import org.junit.Test;

import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.utils.Ints;

import static org.junit.Assert.*;
import nl.jqno.equalsverifier.EqualsVerifier;

public class VertexTest {

	private Vertex testObject;
	private QuorumCertificate qc;
	private Atom atom;
	private Vote vote;
	private VertexMetadata vertexMetadata;

	@Before
	public void setUp() throws Exception {
		Round parentRound = Round.of(1234567890L);
		Integer parentId = 23456;
		Round round = parentRound.next();
		Integer id = 123456;

		this.vertexMetadata = new VertexMetadata(round, id, parentRound, parentId);

		this.vote = new Vote(EUID.TWO, this.vertexMetadata);
		this.qc = new QuorumCertificate(this.vote, this.vertexMetadata);


		this.atom = new Atom();

		this.testObject = Vertex.createVertex(this.qc, round, this.atom);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Vertex.class)
			.verify();
	}

	@Test
	public void testGetters() {
		assertEquals(this.atom, this.testObject.getAtom());
		assertEquals(this.qc, this.testObject.getQC());
		assertEquals(Round.of(1234567891L), this.testObject.getRound());
	}

	@Test
	public void testSerializerConstructor() {
		// Don't want to see any exceptions here
		assertNotNull(new Vertex());
	}
}
