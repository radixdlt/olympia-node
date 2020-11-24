package com.radixdlt;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.JSONFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

interface TestVectorInput {}
interface TestVectorExpected {}
class UnknownTestVector {
	Object input;
	Object expected;
}
abstract class TestVector<Input extends TestVectorInput, Expected extends TestVectorExpected> extends UnknownTestVector {
	abstract Input getInput();
	abstract Expected getExpected();
}

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

					private String source;
					private @Nullable String originalSource;
					private List<UnknownTestVector> vectors;

					Optional<String> getOriginalSource() {
						return Optional.ofNullable(this.originalSource);
					}
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
			String jsonFileContent = Files.asCharSource(file, StandardCharsets.UTF_8).read();
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
	private static final String TEST_SCENARIO_RADIXHASHING = "radix_hashing";
	private static final String TEST_SCENARIO_KEYGEN = "secp256k1";
	private static final String TEST_SCENARIO_KEYSIGN = "ecdsa_signing";
	private static final String TEST_SCENARIO_KEYVERIFY = "ecdsa_verification";
	private static final String TEST_SCENARIO_JSON_ROUNDTRIP_RADIX_PARTICLES = "json_radix_particles";


	private static <T> T cast(Object object, Class<T> toType) {
		Gson gson = new Gson();
		String jsonFromObj = gson.toJson(object);
		return gson.fromJson(jsonFromObj, toType);
	}

	private static <
			Input extends TestVectorInput,
			Expected extends TestVectorExpected,
			Vector extends TestVector<Input, Expected>
			>
	Vector castTestVector(UnknownTestVector unknownTestVector, Class<Vector> toType) {
		return cast(unknownTestVector, toType);
	}

	static final class HashingVectorInput implements TestVectorInput {
		private String stringToHash;
		byte[] bytesToHash() {
			return this.stringToHash.getBytes(StandardCharsets.UTF_8);
		}
	}
	static final class HashingVectorExpected implements TestVectorExpected {
		private String hash;
	}
	static final class HashingTestVector extends TestVector<HashingVectorInput, HashingVectorExpected> {
		@Override HashingVectorInput getInput() {
			return cast(this.input, HashingVectorInput.class);
		}
		@Override HashingVectorExpected getExpected() {
			return cast(this.expected, HashingVectorExpected.class);
		}
	}

	private void testScenarioHashing(SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario scenario) {
		assertEquals(TEST_SCENARIO_HASHING, scenario.identifier);

		BiConsumer<HashingTestVector, Integer> testVectorRunner = (vector, vectorIndex) -> {
			HashingVectorInput input = vector.getInput();
			HashingVectorExpected expected = vector.getExpected();

			MessageDigest hasher = null;
			try {
				hasher = MessageDigest.getInstance("SHA-256");
			} catch (NoSuchAlgorithmException e) {
				throw new AssertionError("Failed to run test, found no hasher", e);
			}
			hasher.update(input.bytesToHash());
			String hashHex = Bytes.toHexString(hasher.digest());

			assertEquals(String.format("Test vector at index %d failed.", vectorIndex), expected.hash, hashHex);
		};

		for (int testVectorIndex = 0; testVectorIndex < scenario.tests.vectors.size(); ++testVectorIndex) {
			UnknownTestVector untypedVector = scenario.tests.vectors.get(testVectorIndex);
			HashingTestVector testVector = castTestVector(untypedVector, HashingTestVector.class);
			testVectorRunner.accept(testVector, testVectorIndex);
		}
	}

	static final class RadixHashingVectorInput implements TestVectorInput {
		private String stringToHash;
		byte[] bytesToHash() {
			return this.stringToHash.getBytes(StandardCharsets.UTF_8);
		}
	}
	static final class RadixHashingVectorExpected implements TestVectorExpected {
		private String hashOfHash;
	}
	static final class RadixHashingTestVector extends TestVector<RadixHashingVectorInput, RadixHashingVectorExpected> {
		@Override RadixHashingVectorInput getInput() {
			return cast(this.input, RadixHashingVectorInput.class);
		}
		@Override RadixHashingVectorExpected getExpected() {
			return cast(this.expected, RadixHashingVectorExpected.class);
		}
	}
	private void testScenarioRadixHashing(SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario scenario) {
		assertEquals(TEST_SCENARIO_RADIXHASHING, scenario.identifier);

		BiConsumer<RadixHashingTestVector, Integer> testVectorRunner = (vector, vectorIndex) -> {
			RadixHashingVectorInput input = vector.getInput();
			RadixHashingVectorExpected expected = vector.getExpected();
			String hashHex = Bytes.toHexString(HashUtils.sha256(input.bytesToHash()).asBytes());
			assertEquals(String.format("Test vector at index %d failed.", vectorIndex), expected.hashOfHash, hashHex);
		};

		for (int testVectorIndex = 0; testVectorIndex < scenario.tests.vectors.size(); ++testVectorIndex) {
			UnknownTestVector untypedVector = scenario.tests.vectors.get(testVectorIndex);
			RadixHashingTestVector testVector = castTestVector(untypedVector, RadixHashingTestVector.class);
			testVectorRunner.accept(testVector, testVectorIndex);
		}
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
		HashMap<String, Consumer<SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario>> map = new HashMap<>();

		map.put(TEST_SCENARIO_HASHING, this::testScenarioHashing);
		map.put(TEST_SCENARIO_RADIXHASHING, this::testScenarioRadixHashing);
		map.put(TEST_SCENARIO_KEYGEN, this::testScenarioKeyGen);
		map.put(TEST_SCENARIO_KEYSIGN, this::testScenarioKeySign);
		map.put(TEST_SCENARIO_KEYVERIFY, this::testScenarioKeyVerify);
		map.put(TEST_SCENARIO_JSON_ROUNDTRIP_RADIX_PARTICLES, this::testScenarioJsonRoundTripRadixParticles);

		return ImmutableMap.copyOf(map);
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
								"Test vectors found at: '%s'\n" +
										"%s" +
								"Failure reason: '%s'\n⚠️⚠️⚠️\n",
						scenario.name,
						scenario.identifier,
						scenario.description.purpose,
						scenario.description.troubleshooting,
						scenario.description.implementationInfo,
						scenario.tests.source,
						scenario.tests.getOriginalSource().map(original -> String.format("Original source: '%s'\n", original)).orElse(""),
						testAssertionError.getLocalizedMessage()
				);

				log.error(failDebugInfo);

				Assert.fail(failDebugInfo);
			}
		}
	}
}
