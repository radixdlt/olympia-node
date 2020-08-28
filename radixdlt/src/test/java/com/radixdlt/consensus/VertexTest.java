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

import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class VertexTest {

	private Vertex testObject;
	private QuorumCertificate qc;
	private Command command;

	@Before
	public void setUp() {
		View baseView = View.of(1234567890L);
		Hash id = Hash.random();

		VertexMetadata vertexMetadata = new VertexMetadata(0, baseView.next(), id, mock(PreparedCommand.class));
		VertexMetadata parent = new VertexMetadata(0, baseView, Hash.random(), mock(PreparedCommand.class));
		VoteData voteData = new VoteData(vertexMetadata, parent, parent);

		this.qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		this.command = mock(Command.class);
		this.testObject = Vertex.createVertex(this.qc, baseView.next().next(), this.command);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Vertex.class)
			.verify();
	}

	@Test
	public void testDirectParentTrue() {
		assertTrue(testObject.hasDirectParent());
	}

	@Test
	public void testDirectParentFalse() {
		View baseView = View.of(1234567890L);
		Hash id = Hash.random();

		VertexMetadata vertexMetadata = new VertexMetadata(0, baseView.next(), id, mock(PreparedCommand.class));
		VertexMetadata parent = new VertexMetadata(0, baseView, Hash.random(), mock(PreparedCommand.class));
		VoteData voteData = new VoteData(vertexMetadata, parent, null);
		QuorumCertificate qc2 = new QuorumCertificate(voteData, new TimestampedECDSASignatures());

		Vertex v = Vertex.createVertex(qc2, baseView.next().next().next(), null);

		assertFalse(v.hasDirectParent());
	}

	@Test
	public void testGetters() {
		assertEquals(this.command, this.testObject.getCommand());
		assertEquals(this.qc, this.testObject.getQC());
		assertEquals(View.of(1234567892L), this.testObject.getView());
	}

}
