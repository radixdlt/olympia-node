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

package com.radixdlt.consensus.epoch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.VerifiedLedgerStateAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import org.junit.Before;
import org.junit.Test;

public class EpochChangeTest {
	private VerifiedLedgerStateAndProof proof;
	private BFTValidatorSet validatorSet;
	private EpochChange epochChange;

	@Before
	public void setup() {
		this.proof = mock(VerifiedLedgerStateAndProof.class);
		when(proof.getEpoch()).thenReturn(323L);
		this.validatorSet = mock(BFTValidatorSet.class);

		this.epochChange = new EpochChange(proof, validatorSet);
	}

	@Test
	public void when_get_next_epoch__then_should_be_epoch_after_proof() {
		assertThat(epochChange.getEpoch()).isEqualTo(324L);
	}

	@Test
	public void when_get_next_ledger_state__then_should_be_epoch_after_proof() {
		assertThat(epochChange.getNextLedgerState().getEpoch()).isEqualTo(324L);
	}
}