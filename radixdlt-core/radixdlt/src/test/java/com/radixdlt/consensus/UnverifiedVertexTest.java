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

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class UnverifiedVertexTest {

	private UnverifiedVertex testObject;
	private QuorumCertificate qc;

	@Before
	public void setUp() {
		View baseView = View.of(1234567890L);
		HashCode id = HashUtils.random256();

		BFTHeader header = new BFTHeader(baseView.next(), id, mock(LedgerHeader.class));
		BFTHeader parent = new BFTHeader(baseView, HashUtils.random256(), mock(LedgerHeader.class));
		VoteData voteData = new VoteData(header, parent, parent);

		this.qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		this.testObject = UnverifiedVertex.createVertex(this.qc, baseView.next().next(), List.of());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(UnverifiedVertex.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}

	@Test
	public void testGetters() {
		assertEquals(this.qc, this.testObject.getQC());
		assertEquals(View.of(1234567892L), this.testObject.getView());
	}

}
