/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.HashUtils;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.crypto.ECDSASignature;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

import nl.jqno.equalsverifier.EqualsVerifier;

import java.util.Optional;

public class ProposalTest {
	private Proposal proposal;
	private UnverifiedVertex vertex;
	private BFTNode node;
	private ECDSASignature signature;
	private QuorumCertificate qc;
	private QuorumCertificate commitQc;

	@Before
	public void setUp() {
		this.vertex = mock(UnverifiedVertex.class);
		this.node = mock(BFTNode.class);
		this.signature = mock(ECDSASignature.class);
		this.commitQc = mock(QuorumCertificate.class);
		this.qc = mock(QuorumCertificate.class);

		when(this.vertex.getQC()).thenReturn(qc);

		this.proposal = new Proposal(vertex, commitQc, node, signature, Optional.empty());
	}

	@Test
	public void testGetters() {
		assertThat(this.proposal.getVertex()).isEqualTo(vertex);
		assertThat(this.proposal.highQC()).isEqualTo(HighQC.from(this.qc, this.commitQc, Optional.empty()));
	}

	@Test
	public void testToString() {
		assertThat(this.proposal).isNotNull();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Proposal.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}

	@Test
	public void sensibleToString() {
		String s = this.proposal.toString();
		assertThat(s).contains(vertex.toString());
	}
}