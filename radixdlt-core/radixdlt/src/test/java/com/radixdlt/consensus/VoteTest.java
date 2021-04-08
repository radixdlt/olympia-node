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

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;

import java.util.Optional;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class VoteTest {
	private BFTNode author;
	private Vote testObject;
	private VoteData voteData;
	private TimestampedVoteData timestampedVoteData;
	private HighQC highQC;

	@Before
	public void setUp() {
		BFTHeader parent = new BFTHeader(View.of(1234567890L), HashUtils.random256(), mock(LedgerHeader.class));
		this.voteData = new VoteData(BFTHeader.ofGenesisAncestor(mock(LedgerHeader.class)), parent, null);
		this.timestampedVoteData = new TimestampedVoteData(this.voteData, 123456L);
		this.author = mock(BFTNode.class);
		this.highQC = mock(HighQC.class);
		this.testObject = new Vote(author, timestampedVoteData, ECDSASignature.zeroSignature(), highQC, Optional.empty());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Vote.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}

	@Test
	public void testGetters() {
		assertEquals(this.testObject.getEpoch(), voteData.getProposed().getLedgerHeader().getEpoch());
		assertEquals(this.voteData, this.testObject.getVoteData());
		assertEquals(this.author, this.testObject.getAuthor());
		assertEquals(this.highQC, this.testObject.highQC());
	}

	@Test
	public void testToString() {
		assertThat(this.testObject.toString())
			.isNotNull()
			.contains(Vote.class.getSimpleName())
			.contains("epoch=0")
			.contains("view=0")
			.contains(this.author.toString());
	}
}
