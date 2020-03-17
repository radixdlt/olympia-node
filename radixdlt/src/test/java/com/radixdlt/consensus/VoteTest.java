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
import com.radixdlt.common.EUID;
import com.radixdlt.utils.Ints;

import static org.junit.Assert.*;
import nl.jqno.equalsverifier.EqualsVerifier;

public class VoteTest {

	private Vote testObject;
	private VertexMetadata vertexMetadata;

	@Before
	public void setUp() throws Exception {
		Round parentRound = Round.of(1234567890L);
		Integer parentId = 23456;

		Round round = parentRound.next();
		Integer id = 123456;

		EUID author = EUID.TWO;
		this.vertexMetadata = new VertexMetadata(round, id, parentRound, parentId);

		this.testObject = new Vote(author, vertexMetadata);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Vote.class)
			.verify();
	}

	@Test
	public void testGetters() {
		assertEquals(this.vertexMetadata, this.testObject.getVertexMetadata());
		assertEquals(EUID.TWO, this.testObject.getAuthor());
	}

	@Test
	public void testSerializerConstructor() {
		// Don't want to see any exceptions here
		assertNotNull(new Vote());
	}

	private static AID aidOf(int id) {
		byte[] bytes = new byte[AID.BYTES];
		Ints.copyTo(id, bytes, AID.BYTES - Integer.BYTES);
		return AID.from(bytes);
	}
}
