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
import com.radixdlt.common.AID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Ints;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class VoteTest {
	public static final RadixAddress ADDRESS = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
	private Vote testObject;
	private VertexMetadata vertexMetadata;
	private Hash parentId;
	private Hash id;

	@Before
	public void setUp() {
		View parentView = View.of(1234567890L);
		this.parentId = Hash.random();

		View view = parentView.next();
		this.id = Hash.random();

		this.vertexMetadata = new VertexMetadata(view, id, parentView, parentId);

		this.testObject = new Vote(ADDRESS.getKey(), vertexMetadata, null);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Vote.class)
			.verify();
	}

	@Test
	public void testGetters() {
		assertEquals(this.vertexMetadata, this.testObject.getVertexMetadata());
		assertEquals(ADDRESS.getKey(), this.testObject.getAuthor());
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
