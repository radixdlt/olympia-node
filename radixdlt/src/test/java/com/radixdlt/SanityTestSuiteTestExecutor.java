package com.radixdlt;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.utils.JSONFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.collect.Streams.zip;
import static org.junit.Assert.assertEquals;

public class SanityTestSuiteTestExecutor {

	private static final Logger log = LogManager.getLogger();

	static final class SanityTestSuiteRoot {

		static final class SanityTestSuite {

			/**
			 * Test scenario on structure:
			 *
			 *            {
			 * 				"description": {
			 * 					"implementationInfo": "",
			 * 					"purpose": "",
			 * 					"troubleshooting": ""
			 *                },
			 * 				"identifier": "",
			 * 				"name": "",
			 * 				"tests": {
			 * 					"source": "https://",
			 * 					"vectors": [
			 *                        {
			 * 							"input": {},
			 * 							"expected": {}
			 *                        }
			 * 					]
			 *                }
			 *            }
			 */
			static final class SanityTestScenario {
				static final class SanityTestScenarioDescription {
					private String implementationInfo;
					private String purpose;
					private String troubleshooting;
				}

				static final class SanityTestScenarioTests {
					static final class SanityTestScenarioTestVector {
						private JSONObject input;
						private JSONObject expected;
					}

					private String source;
					private List<SanityTestScenarioTestVector> vectors;
				}

				private SanityTestScenarioDescription description;
				private String identifier;
				private String name;
				private SanityTestScenarioTests tests;

			}

			private List<SanityTestScenario> scenarios;

		}

		private String hashOfSuite;
		private SanityTestSuite suite;

	}

	private SanityTestSuiteRoot sanityTestSuiteRootFromFileNamed(String sanityTestJSONFileName) {
		Gson gson = new Gson();
		JsonReader reader = null;
		try {
			ClassLoader classLoader = getClass().getClassLoader();
			File file = new File(classLoader.getResource(sanityTestJSONFileName).getFile());

			// Compare saved hash in file with calculated hash of test.
			String jsonFileContent = Files.asCharSource(file, Charsets.UTF_8).read();
			JSONObject sanityTestSuiteRootAsJsonObject = new JSONObject(jsonFileContent);
			String sanityTestSuiteSavedHash = sanityTestSuiteRootAsJsonObject.getString("hashOfSuite");
			JSONObject sanityTestSuiteAsJsonObject = sanityTestSuiteRootAsJsonObject.getJSONObject("suite");
			String sanityTestSuiteAsJsonStringPretty = JSONFormatter.sortPrettyPrintJSONString(sanityTestSuiteAsJsonObject.toString(4));
			Hasher hasher = Sha256Hasher.withDefaultSerialization();
			HashCode calculatedHashOfSanityTestSuite = hasher.hash(sanityTestSuiteAsJsonStringPretty);
			assertEquals(sanityTestSuiteSavedHash, calculatedHashOfSanityTestSuite.toString());

			FileReader fileReader = new FileReader(file);
			reader = new JsonReader(fileReader);
		} catch (Exception e) {
			throw new IllegalStateException("failed to load test vectors, e: " + e);
		}

		SanityTestSuiteRoot sanityTestSuiteRoot = gson.fromJson(reader, SanityTestSuiteRoot.class);

		return sanityTestSuiteRoot;

	}

	private SanityTestSuiteRoot sanityTestSuiteRootFromFile() {
		return sanityTestSuiteRootFromFileNamed("sanity_test_suite.json");
	}

	private static final String TEST_SCENARIO_HASHING = "hashing";
	private static final String TEST_SCENARIO_KEYGEN = "secp256k1";
	private static final String TEST_SCENARIO_KEYSIGN = "ecdsa_signing";
	private static final String TEST_SCENARIO_KEYVERIFY = "ecdsa_verification";
	private static final String TEST_SCENARIO_JSON_ROUNDTRIP_RADIX_PARTICLES = "json_radix_particles";

	private void testScenarioHashing(SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario scenario) {
		assertEquals(TEST_SCENARIO_HASHING, scenario.identifier);
	}

	private void testScenarioKeyGen(SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario scenario) {
		assertEquals(TEST_SCENARIO_KEYGEN, scenario.identifier);
	}

	private void testScenarioKeySign(SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario scenario) {
		assertEquals(TEST_SCENARIO_KEYSIGN, scenario.identifier);
	}

	private void testScenarioKeyVerify(SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario scenario) {
		assertEquals(TEST_SCENARIO_KEYVERIFY, scenario.identifier);
	}

	private void testScenarioJsonRoundTripRadixParticles(SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario scenario) {
		assertEquals(TEST_SCENARIO_JSON_ROUNDTRIP_RADIX_PARTICLES, scenario.identifier);
	}


	private Map<String, Consumer<SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario>> makeScenarioRunnerMap() {
		return ImmutableMap.of(
				TEST_SCENARIO_HASHING, this::testScenarioHashing,
				TEST_SCENARIO_KEYGEN, this::testScenarioKeyGen,
				TEST_SCENARIO_KEYSIGN, this::testScenarioKeySign,
				TEST_SCENARIO_KEYVERIFY, this::testScenarioKeyVerify,
				TEST_SCENARIO_JSON_ROUNDTRIP_RADIX_PARTICLES, this::testScenarioJsonRoundTripRadixParticles
		);
	}

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

				String failDebugInfo = String.format(
								"\n⚠️⚠️⚠️\nFailed test scenario: '%s'\n" +
								"Identifier: '%s'\n" +
								"Purpose of scenario: '%s'\n" +
								"Troubleshooting: '%s'\n" +
								"Implementation info: '%s'\n" +
								"Failure reason: '%s'\n⚠️⚠️⚠️\n",
						scenario.name,
						scenario.identifier,
						scenario.description.purpose,
						scenario.description.troubleshooting,
						scenario.description.implementationInfo,
						testAssertionError.getLocalizedMessage()
				);

				log.error(failDebugInfo);

				Assert.fail(failDebugInfo);
			}
		}
	}
}
