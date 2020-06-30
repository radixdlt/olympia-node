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

package com.radixdlt.consensus.simulation.epochs;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.consensus.simulation.BFTCheck.BFTCheckError;
import com.radixdlt.consensus.simulation.BFTSimulatedTest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

public class StaticValidatorEpochChangePer100ViewsTest {
	@Test
	@Ignore("Need to fix tests to support epochs")
	public void given_4_correct_bfts__then_should_pass_sanity_tests_over_1_minute() {
		BFTSimulatedTest bftTest = BFTSimulatedTest.builder()
			.numNodes(4)
			.setIsSingleEpoch(false)
			.checkSafety("safety")
			.checkLiveness("liveness")
			.checkNoTimeouts("noTimeouts")
			.checkAllProposalsHaveDirectParents("directParents")
			.build();
		Map<String, Optional<BFTCheckError>> results = bftTest.run(1, TimeUnit.MINUTES);
		assertThat(results).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}
}
