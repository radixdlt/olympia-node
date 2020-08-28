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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger_radixengine;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.TestInvariant.TestInvariantError;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

/**
 * Randomly registers and unregisters nodes as validators
 */
public class RandomValidatorsTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.numInitialValidators(2)
		.ledgerAndRadixEngineWithEpochHighView(View.of(10))
		.checkEpochHighView("epochHighView", View.of(100))
		.checkSafety("safety")
		.checkLiveness("liveness", 1000, TimeUnit.MILLISECONDS)
		.checkNoTimeouts("noTimeouts")
		.checkAllProposalsHaveDirectParents("directParents")
		.addRadixEngineValidatorRegisterUnregisterMempoolSubmissions("mempoolSubmitted");

	@Test
	public void when_increasing_validators__then_they_should_be_getting_registered() {
		SimulationTest simulationTest = bftTestBuilder
			.build();
		Map<String, Optional<TestInvariantError>> results = simulationTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

}
