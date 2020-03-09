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

import com.radixdlt.crypto.DefaultSignatures;
import com.radixdlt.crypto.ECPublicKey;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.utils.Ints;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
		AID parentAid = aidOf(23456);
		Round round = parentRound.next();
		AID aid = aidOf(123456);

		this.vertexMetadata = new VertexMetadata(round, aid, parentRound, parentAid);

		this.vote = new Vote(makePubKey(EUID.TWO), this.vertexMetadata, null);
		this.qc = new QuorumCertificate(this.vertexMetadata, DefaultSignatures.emptySignatures());


		this.atom = new Atom();

		this.testObject = new Vertex(this.qc, round, this.atom);
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

	private static AID aidOf(int id) {
		byte[] bytes = new byte[AID.BYTES];
		Ints.copyTo(id, bytes, AID.BYTES - Integer.BYTES);
		return AID.from(bytes);
	}

	private static ECPublicKey makePubKey(EUID id) {
		ECPublicKey pubKey = mock(ECPublicKey.class);
		when(pubKey.getUID()).thenReturn(id);
		return pubKey;
	}
}
