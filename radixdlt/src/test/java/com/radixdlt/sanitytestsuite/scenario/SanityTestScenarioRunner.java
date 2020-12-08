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

package com.radixdlt.sanitytestsuite.scenario;

import com.google.gson.reflect.TypeToken;
import com.radixdlt.sanitytestsuite.SanityTestSuiteTestLoader;
import com.radixdlt.sanitytestsuite.model.SanityTestSuiteRoot;
import com.radixdlt.sanitytestsuite.model.SanityTestVector;
import com.radixdlt.sanitytestsuite.model.UnknownTestVector;
import com.radixdlt.utils.JSONFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;

public abstract class SanityTestScenarioRunner<TestVector extends SanityTestVector> {

	private static final Logger log = LogManager.getLogger();

	public abstract String testScenarioIdentifier();

	public abstract TypeToken<TestVector> typeOfVector();


	public abstract void doRunTestVector(TestVector testVector) throws AssertionError;

	public void executeTest(SanityTestSuiteRoot.SanityTestSuite.SanityTestScenario scenario) {
		assertEquals(testScenarioIdentifier(), scenario.identifier);

		for (int testVectorIndex = 0; testVectorIndex < scenario.tests.vectors.size(); ++testVectorIndex) {
			UnknownTestVector untypedVector = scenario.tests.vectors.get(testVectorIndex);
			TestVector testVector = cast(untypedVector, this.typeOfVector());
			try {
				doRunTestVector(testVector);
			} catch (AssertionError e) {
				String msg = String.format(
					"Failing test vector index: %d, vector: %s",
					testVectorIndex,
					prettyJsonStringFromObject(testVector)
				);
				throw new AssertionError(msg, e);
			}
		}
	}

	public static byte[] sha256Hash(byte[] bytes) {
		MessageDigest hasher = null;
		try {
			hasher = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError("Failed to run test, found no hasher", e);
		}
		hasher.update(bytes);
		return hasher.digest();
	}

	protected String prettyJsonStringFromObject(Object object) {
		String jsonStringPretty = JSONFormatter.sortPrettyPrintJSONString(SanityTestSuiteTestLoader.gson.toJson(object));
		return jsonStringPretty;
	}

	private <T> T cast(Object object, TypeToken<T> typeToken) {
		String jsonFromObj = SanityTestSuiteTestLoader.gson.toJson(object);
		return SanityTestSuiteTestLoader.gson.fromJson(jsonFromObj, typeToken.getType());
	}

}