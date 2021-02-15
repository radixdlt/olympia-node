/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.sanitytestsuite;

import com.google.common.collect.ImmutableList;
import com.radixdlt.sanitytestsuite.model.SanityTestSuiteRoot;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.deserialization.DeserializationTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.hashing.HashingTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.keygen.KeyGenTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.keysign.KeySignTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.keyverify.KeyVerifyTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.radixhashing.RadixHashingTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.serialization.SerializationTestScenarioRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;

public final class SanityTestSuiteExecutor {
	private static final Logger LOG = LogManager.getLogger();
	private static final String SANITY_TEST_SUITE_JSON_FILE_NAME = "sanity_test_suite.json";

	private final List<SanityTestScenarioRunner<?>> testScenarios = ImmutableList.of(
		new HashingTestScenarioRunner(),
		new RadixHashingTestScenarioRunner(),
		new KeyGenTestScenarioRunner(),
		new KeySignTestScenarioRunner(),
		new KeyVerifyTestScenarioRunner(),
		new DeserializationTestScenarioRunner(),
		new SerializationTestScenarioRunner()
	);

	@Test
	public void test_sanity_suite() {
		var sanityTestSuiteRoot = sanityTestSuiteRootFromFile();
		var scenarioRunnerMap = makeScenarioRunnerMap();

		assertEquals(
			scenarioRunnerMap.keySet(),
			sanityTestSuiteRoot.suite.scenarios.stream()
				.map(s -> s.identifier)
				.collect(Collectors.toSet())
		);

		for (var scenario : sanityTestSuiteRoot.suite.scenarios) {
			var scenarioRunner = scenarioRunnerMap.get(scenario.identifier);
			// Run test scenario
			LOG.debug("🔮 Running scenario: {}", scenario.name);

			try {
				scenarioRunner.accept(scenario);
				LOG.info("✅ Test of scenario '{}' passed", scenario.name);
			} catch (AssertionError testAssertionError) {
				var failDebugInfo = scenario.failDescriptionWithAssertionError(testAssertionError);
				LOG.error(failDebugInfo);
				throw new AssertionError(failDebugInfo, testAssertionError);
			}
		}
	}

	private SanityTestSuiteRoot sanityTestSuiteRootFromFile() {
		return new SanityTestSuiteTestLoader()
			.sanityTestSuiteRootFromFileNamed(SANITY_TEST_SUITE_JSON_FILE_NAME);
	}

	private Map<String, Consumer<SanityTestSuiteRoot.Suite.Scenario>> makeScenarioRunnerMap() {
		return testScenarios.stream()
			.collect(
				toMap(
					SanityTestScenarioRunner::testScenarioIdentifier,
					s -> scenario -> s.executeTest(scenario)
				)
			);
	}
}
