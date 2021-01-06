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

package com.radixdlt.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class DtoLedgerHeaderAndProofTest {
	private DtoLedgerHeaderAndProof ledgerHeaderAndProof;
	private BFTHeader opaque0;
	private BFTHeader opaque1;
	private long opaque2 = 12345;
	private HashCode opaque3;
	private LedgerHeader ledgerHeader;
	private TimestampedECDSASignatures signatures;

	@Before
	public void setup() {
		this.opaque0 = mock(BFTHeader.class);
		this.opaque1 = mock(BFTHeader.class);
		this.opaque3 = mock(HashCode.class);
		this.ledgerHeader = mock(LedgerHeader.class);
		this.signatures = mock(TimestampedECDSASignatures.class);
		this.ledgerHeaderAndProof = new DtoLedgerHeaderAndProof(
			opaque0, opaque1, opaque2, opaque3, ledgerHeader, signatures
		);
	}

	@Test
	public void when_get_vote_data__then_should_not_be_null() {
		VoteData voteData = ledgerHeaderAndProof.toVoteData();
		assertThat(voteData).isNotNull();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(DtoLedgerHeaderAndProof.class)
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}
}