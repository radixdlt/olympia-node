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

import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.simulation.SimulationTest.Builder;
import com.radixdlt.consensus.simulation.TestInvariant.TestInvariantError;
import com.radixdlt.consensus.simulation.SimulationTest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class StaticValidatorsTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.checkSafety("safety")
		.checkLiveness("liveness", 1000, TimeUnit.MILLISECONDS)
		.checkNoTimeouts("noTimeouts")
		.checkAllProposalsHaveDirectParents("directParents");

	@Test
	public void given_correct_bft_with_changing_epochs_every_view__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.pacemakerTimeout(1000)
			.epochHighView(View.of(1))
			.checkEpochHighView("epochHighView", View.of(1))
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}

	@Test
	public void given_correct_bft_with_changing_epochs_per_100_views__then_should_fail_incorrect_epoch_invariant() {
		SimulationTest bftTest = bftTestBuilder
			.epochHighView(View.of(100))
			.checkEpochHighView("epochHighView", View.of(99))
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).hasEntrySatisfying("epochHighView", error -> assertThat(error).isPresent());
	}

	@Test
	public void given_correct_bft_with_changing_epochs_per_100_views__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.epochHighView(View.of(100))
			.checkEpochHighView("epochHighView", View.of(100))
			.build();
		Map<String, Optional<TestInvariantError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}
}
