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

package com.radixdlt.consensus.simulation.tests.epochs;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.consensus.View;
import com.radixdlt.consensus.simulation.TestInvariant.TestInvariantError;
import com.radixdlt.consensus.simulation.SimulatedTest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class EpochsStaticValidatorTest {
	@Test
	public void given_correct_bft_with_changing_epochs_every_view__then_should_pass_bft_invariants() {
		SimulatedTest bftTest = SimulatedTest.builder()
			.pacemakerTimeout(1000)
			.numNodes(4)
			.epochHighView(View.of(1))
			.checkSafety("safety")
			.checkLiveness("liveness")
			.checkNoTimeouts("noTimeouts")
			.checkAllProposalsHaveDirectParents("directParents")
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}

	@Test
	public void given_correct_bft_with_changing_epochs_per_100_views__then_should_pass_bft_invariants() {
		SimulatedTest bftTest = SimulatedTest.builder()
			.numNodes(4)
			.epochHighView(View.of(100))
			.checkSafety("safety")
			.checkLiveness("liveness")
			.checkNoTimeouts("noTimeouts")
			.checkAllProposalsHaveDirectParents("directParents")
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}
}
