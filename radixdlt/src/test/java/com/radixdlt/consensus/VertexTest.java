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

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.common.Atom;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.Hash;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class VertexTest {

	private Vertex testObject;
	private QuorumCertificate qc;
	private Atom atom;
	private Vote vote;
	private VertexMetadata vertexMetadata;
	private Hash parentId;
	private Hash id;

	@Before
	public void setUp() throws Exception {
		View parentView = View.of(1234567890L);
		this.parentId = Hash.random();
		View view = parentView.next();
		this.id = Hash.random();

		this.vertexMetadata = new VertexMetadata(view, id, parentView, parentId);

		RadixAddress author = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		this.vote = new Vote(author.getKey(), this.vertexMetadata, null);
		this.qc = new QuorumCertificate(this.vertexMetadata, new ECDSASignatures());


		this.atom = new Atom();

		this.testObject = Vertex.createVertex(this.qc, view, this.atom);
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
		assertEquals(View.of(1234567891L), this.testObject.getView());
	}

	@Test
	public void testSerializerConstructor() {
		// Don't want to see any exceptions here
		assertNotNull(new Vertex());
	}
}
