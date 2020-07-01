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

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.crypto.Hash;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class VoteTest {
	public static final RadixAddress ADDRESS = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
	private Vote testObject;
	private VoteData voteData;

	@Before
	public void setUp() {
		VertexMetadata parent = new VertexMetadata(0, View.of(1234567890L), Hash.random(), 1, false);
		this.voteData = new VoteData(VertexMetadata.ofGenesisAncestor(), parent, null);

		this.testObject = new Vote(ADDRESS.getPublicKey(), voteData, null);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Vote.class)
			.verify();
	}

	@Test
	public void testGetters() {
		assertEquals(this.testObject.getEpoch(), voteData.getProposed().getEpoch());
		assertEquals(this.voteData, this.testObject.getVoteData());
		assertEquals(ADDRESS.getPublicKey(), this.testObject.getAuthor());
	}


	@Test
	public void testToString() {
		assertThat(this.testObject.toString()).isNotNull();
	}

	@Test
	public void testSerializerConstructor() {
		// Don't want to see any exceptions here
		assertNotNull(new Vote());
	}
}
