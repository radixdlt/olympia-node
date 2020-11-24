package com.radixdlt;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.radixdlt.utils.JSONFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;

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

//			private String toJSONString()
//
//			private boolean isCanonicalJSON() {
//
//				JSONFormatter.sortPrettyPrintJSONString(nonCanonicalJSONString);
//			}
//
//			String calculateHash() {
//
//			}
		}

		private String hashOfSuite;
		private SanityTestSuite suite;

//		boolean validate() {
//
//		}
//
//		private boolean validateHash() {
//
//		}
//
//		private String calculateHashOfSuite() {
//			return this.suite.calculateHash();
//		}
	}

	private SanityTestSuiteRoot sanityTestSuiteRootFromFileNamed(String sanityTestJSONFileName) {
		Gson gson = new Gson();
		JsonReader reader = null;
		try {
			ClassLoader classLoader = getClass().getClassLoader();
			File file = new File(classLoader.getResource(sanityTestJSONFileName).getFile());
			FileReader fileReader = new FileReader(file);
			reader = new JsonReader(fileReader);
		} catch (FileNotFoundException e) {
			throw new IllegalStateException("failed to load test vectors, e: " + e);
		}

		SanityTestSuiteRoot sanityTestSuiteRoot = gson.fromJson(reader, SanityTestSuiteRoot.class);
		String rawJSONString = reader.toString();

		log.error(rawJSONString);

		return sanityTestSuiteRoot;

	}

	private SanityTestSuiteRoot sanityTestSuiteRootFromFile() {
		return sanityTestSuiteRootFromFileNamed("sanity_test_suite.json");
	}

	@Test
	public void test_sanity_suite() {
		SanityTestSuiteRoot sanityTestSuiteRoot = sanityTestSuiteRootFromFile();
		assertEquals(5, sanityTestSuiteRoot.suite.scenarios.size());
	}
}
