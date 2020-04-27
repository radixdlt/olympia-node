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

package com.radixdlt.consensus.liveness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;
import java.util.Comparator;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;

public class WeightedRotatingLeadersTest {
	private final int validatorSetSize = 100;
	private WeightedRotatingLeaders weightedRotatingLeaders;
	private WeightedRotatingLeaders weightedRotatingLeaders2;
	private ImmutableList<Validator> validatorsInOrder;

	@Before
	public void setUp() {
		final int sizeOfCache = 100;

		validatorsInOrder = IntStream.range(0, validatorSetSize)
			.boxed().map(i -> mock(ECPublicKey.class))
			.map(pk -> Validator.from(pk, UInt256.ONE))
			.collect(ImmutableList.toImmutableList());

		ValidatorSet validatorSet = ValidatorSet.from(validatorsInOrder);
		this.weightedRotatingLeaders = new WeightedRotatingLeaders(validatorSet, Comparator.comparingInt(validatorsInOrder::indexOf), sizeOfCache);
		this.weightedRotatingLeaders2 = new WeightedRotatingLeaders(validatorSet, Comparator.comparingInt(validatorsInOrder::indexOf), sizeOfCache);
	}

	@Test
	public void when_equivalent_leaders__then_leaders_are_round_robined_deterministically() {
		final int viewsToTest = 1000;

		for (int view = 0; view < viewsToTest; view++) {
			ECPublicKey expectedKeyForView = validatorsInOrder.get(validatorSetSize - (view % validatorSetSize) - 1).nodeKey();
			assertThat(weightedRotatingLeaders.getProposer(View.of(view))).isEqualTo(expectedKeyForView);
		}
	}

	@Test
	public void when_get_proposer_multiple_times__then_should_return_the_same_key() {
		final int viewsToTest = 1000;

		ECPublicKey expectedKeyForView0 = weightedRotatingLeaders.getProposer(View.of(0));
		for (View view = View.of(1); view.compareTo(View.of(viewsToTest)) <= 0; view = view.next()) {
			weightedRotatingLeaders.getProposer(view);
		}
		assertThat(weightedRotatingLeaders.getProposer(View.of(0))).isEqualTo(expectedKeyForView0);
	}

	@Test
	public void when_get_proposer_skipping_views__then_should_return_same_result_as_in_order() {
		final int viewsToTest = 1000;

		for (int view = 0; view < viewsToTest; view++) {
			weightedRotatingLeaders2.getProposer(View.of(view));
		}

		ECPublicKey pk1 = weightedRotatingLeaders.getProposer(View.of(viewsToTest - 1));
		ECPublicKey pk2 = weightedRotatingLeaders2.getProposer(View.of(viewsToTest - 1));
		assertThat(pk1).isEqualTo(pk2);
	}
}