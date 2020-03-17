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

import com.radixdlt.common.EUID;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.common.AID;
import com.radixdlt.utils.Ints;

import static org.junit.Assert.*;
import nl.jqno.equalsverifier.EqualsVerifier;

public class VertexMetadataTest {

	private VertexMetadata testObject;

	@Before
	public void setUp() throws Exception {
		Round parentRound = Round.of(1234567890L);
		EUID parentId = new EUID(23456);

		Round round = parentRound.next();
		EUID id = new EUID(123456);

		this.testObject = new VertexMetadata(round, id, parentRound, parentId);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(VertexMetadata.class)
			.verify();
	}

	@Test
	public void testGetters() {
		assertEquals(Round.of(1234567890L), this.testObject.getParentRound());
		assertEquals(Round.of(1234567891L), this.testObject.getRound());

		assertEquals(new EUID(23456), this.testObject.getParentId());
		assertEquals(new EUID(123456), this.testObject.getId());
	}

	@Test
	public void testSerializerConstructor() {
		// Don't want to see any exceptions here
		assertNotNull(new VertexMetadata());
	}
}
