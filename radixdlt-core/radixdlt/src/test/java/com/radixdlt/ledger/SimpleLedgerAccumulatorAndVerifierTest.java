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
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.atom.Txn;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.HashUtils;
import org.junit.Before;
import org.junit.Test;

public class SimpleLedgerAccumulatorAndVerifierTest {
	private SimpleLedgerAccumulatorAndVerifier accumulatorAndVerifier;
	private Hasher hasher;

	@Before
	public void setup() {
		hasher = Sha256Hasher.withDefaultSerialization();
		accumulatorAndVerifier = new SimpleLedgerAccumulatorAndVerifier(hasher);
	}

	@Test
	public void when_accumulate__then_should_verify() {
		AccumulatorState headState = new AccumulatorState(345, HashUtils.zero256());
		AccumulatorState nextState = accumulatorAndVerifier.accumulate(headState, HashUtils.zero256());
		assertThat(accumulatorAndVerifier.verify(headState, ImmutableList.of(HashUtils.zero256()), nextState)).isTrue();
	}

	@Test
	public void when_empty_command_truncate_from_bad_version__then_should_throw_exception() {
		AccumulatorState curState = mock(AccumulatorState.class);
		when(curState.getStateVersion()).thenReturn(1234L);
		AccumulatorState nextState = mock(AccumulatorState.class);
		when(nextState.getStateVersion()).thenReturn(1235L);

		assertThat(accumulatorAndVerifier.verifyAndGetExtension(curState, ImmutableList.of(), i -> null, nextState))
			.isEmpty();
	}

	@Test
	public void when_empty_command_truncate_from_perfect_version__then_should_return_empty_list() {
		AccumulatorState state = mock(AccumulatorState.class);
		when(state.getStateVersion()).thenReturn(1234L);
		when(state.getAccumulatorHash()).thenReturn(mock(HashCode.class));

		assertThat(accumulatorAndVerifier.verifyAndGetExtension(state, ImmutableList.of(), i -> null, state))
			.hasValue(ImmutableList.of());
	}

	@Test
	public void when_single_command_truncate_from_perfect_version__then_should_return_equivalent() {
		var txn = Txn.create(new byte[]{0});
		AccumulatorState headState = new AccumulatorState(345, HashUtils.zero256());
		AccumulatorState nextState = accumulatorAndVerifier.accumulate(headState, txn.getId().asHashCode());
		assertThat(accumulatorAndVerifier.verifyAndGetExtension(headState, ImmutableList.of(txn), t -> t.getId().asHashCode(), nextState))
			.hasValue(ImmutableList.of(txn));
	}
}