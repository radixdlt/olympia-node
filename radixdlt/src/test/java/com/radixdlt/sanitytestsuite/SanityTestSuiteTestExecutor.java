package com.radixdlt.sanitytestsuite;

import com.google.common.collect.ImmutableList;
import com.radixdlt.sanitytestsuite.model.SanityTestSuiteRoot;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.hashing.HashingTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.jsonparticles.JsonRadixParticlesTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.keygen.KeyGenTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.keysign.KeySignTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.keyverify.KeyVerifyTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.radixhashing.RadixHashingTestScenarioRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;

public class SanityTestSuiteTestExecutor {

	private static final Logger log = LogManager.getLogger();
	private static final String SANITY_TEST_SUITE_JSON_FILE_NAME = "sanity_test_suite.json";

	private final List<SanityTestScenarioRunner<?>> testScenarios = ImmutableList.of(
			new HashingTestScenarioRunner(),
			new RadixHashingTestScenarioRunner(),
			new KeyGenTestScenarioRunner(),
			new KeySignTestScenarioRunner(),
			new KeyVerifyTestScenarioRunner(),
			new JsonRadixParticlesTestScenarioRunner()
	);

	@Test
	public void test_sanity_suite() {
		SanityTestSuiteRoot sanityTestSuiteRoot = sanityTestSuiteRootFromFile();
		Map<String, Consumer<SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario>> scenarioRunnerMap = makeScenarioRunnerMap();
		assertEquals(
				scenarioRunnerMap.keySet(),
				sanityTestSuiteRoot.suite.scenarios.stream().map(s -> s.identifier).collect(Collectors.toSet())
		);

		for (SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario scenario : sanityTestSuiteRoot.suite.scenarios) {
			Consumer<SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario> scenarioRunner = scenarioRunnerMap.get(scenario.identifier);
			// Run test scenario
			log.debug(String.format("🔮 Running scenario: %s", scenario.name));

			try {
				scenarioRunner.accept(scenario);
				log.info(String.format("✅ Test of scenario '%s' passed", scenario.name));
			} catch (AssertionError testAssertionError) {
				String failDebugInfo = scenario.failDescriptionWithAssertionError(testAssertionError);
				log.error(failDebugInfo);
				Assert.fail(failDebugInfo);
			}
		}
	}

	private SanityTestSuiteRoot sanityTestSuiteRootFromFile() {
		return new SanityTestSuiteTestLoader().sanityTestSuiteRootFromFileNamed(SANITY_TEST_SUITE_JSON_FILE_NAME);
	}

	private Map<String, Consumer<SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario>> makeScenarioRunnerMap() {
		return testScenarios.stream()
				.collect(
						toMap(
								SanityTestScenarioRunner::testScenarioIdentifier,
								s -> s::executeTest
						)
				);
	}
}
