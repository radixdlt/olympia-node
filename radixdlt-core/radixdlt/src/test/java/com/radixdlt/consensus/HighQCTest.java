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
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.ledger.AccumulatorState;
import nl.jqno.equalsverifier.EqualsVerifier;

import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.serialization.SerializeObject;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class HighQCTest extends SerializeObject<HighQC> {
	public HighQCTest() {
		super(HighQC.class, HighQCTest::get);
	}

	private static HighQC get() {
		View view = View.of(1234567891L);
		HashCode id = HashUtils.random256();

		var accumulatorState = new AccumulatorState(0, HashUtils.zero256());
		LedgerHeader ledgerHeader = LedgerHeader.genesis(accumulatorState, null);
		BFTHeader header = new BFTHeader(view, id, ledgerHeader);
		BFTHeader parent = new BFTHeader(View.of(1234567890L), HashUtils.random256(), ledgerHeader);
		BFTHeader commit = new BFTHeader(View.of(1234567889L), HashUtils.random256(), ledgerHeader);
		VoteData voteData = new VoteData(header, parent, commit);
		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		return HighQC.from(qc, qc, Optional.empty());
	}

	@Test
	public void when_created_with_equal_qcs__highest_committed_is_elided() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		HighQC highQC = HighQC.from(qc, qc, Optional.empty());
		QuorumCertificate storedCommitQC = Whitebox.getInternalState(highQC, "highestCommittedQC");
		assertThat(storedCommitQC).isNull();
		assertThat(highQC.highestQC()).isEqualTo(qc);
		assertThat(highQC.highestCommittedQC()).isEqualTo(qc);
	}

	@Test
	public void when_created_with_unequal_qcs__highest_committed_is_stored() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		QuorumCertificate cqc = mock(QuorumCertificate.class);
		HighQC highQC = HighQC.from(qc, cqc, Optional.empty());
		QuorumCertificate storedCommitQC = Whitebox.getInternalState(highQC, "highestCommittedQC");
		assertThat(storedCommitQC).isEqualTo(cqc);
		assertThat(highQC.highestQC()).isEqualTo(qc);
		assertThat(highQC.highestCommittedQC()).isEqualTo(cqc);
	}

	@Test
	public void sensibleToString() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		QuorumCertificate cqc = mock(QuorumCertificate.class);
		HighQC highQC1 = HighQC.from(qc, cqc, Optional.empty());

		String s1 = highQC1.toString();
		assertThat(s1)
			.contains(HighQC.class.getSimpleName())
			.contains(qc.toString())
			.contains(cqc.toString());

		HighQC highQC2 = HighQC.from(qc, qc, Optional.empty());
		String s2 = highQC2.toString();
		assertThat(s2)
			.contains(HighQC.class.getSimpleName())
			.contains(qc.toString())
			.contains("<same>");
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(HighQC.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}
}
