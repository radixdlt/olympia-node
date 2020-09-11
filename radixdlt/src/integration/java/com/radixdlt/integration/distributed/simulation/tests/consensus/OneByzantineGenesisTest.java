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

package com.radixdlt.integration.distributed.simulation.tests.consensus;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.TestInvariant.TestInvariantError;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

/**
 * Tests that progress cannot be made if nodes do not form a quorum on the
 * genesis hash.
 */
public class OneByzantineGenesisTest {
	SimulationTest.Builder bftTestBuilder = SimulationTest.builder()
		.pacemakerTimeout(1000)
		.checkConsensusSafety("safety");

	@Test
	public void given_2_correct_bfts_and_1_incorrect__then_should_never_make_progress() {
		SimulationTest bftTest = bftTestBuilder
			.numNodes(3)
			.modifyOneGenesis(true)
			.checkConsensusNoneCommitted("noneCommitted")
			.build();

		Map<String, Optional<TestInvariantError>> results = bftTest.run();
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

	@Test
	public void given_3_correct_bfts__then_none_committed_invariant_should_fail() {
		SimulationTest bftTest = bftTestBuilder
			.numNodes(3)
			.checkConsensusNoneCommitted("noneCommitted")
			.build();

		Map<String, Optional<TestInvariantError>> results = bftTest.run();
		assertThat(results).hasEntrySatisfying("noneCommitted", error -> assertThat(error).isPresent());
	}

	@Test
	public void given_3_correct_bfts_and_1_incorrect__then_should_make_progress() {
		SimulationTest bftTest = bftTestBuilder
			.numNodes(4)
			.modifyOneGenesis(true)
			.checkConsensusLiveness("liveness", 5, TimeUnit.SECONDS)
			.build();

		Map<String, Optional<TestInvariantError>> results = bftTest.run();
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

}
