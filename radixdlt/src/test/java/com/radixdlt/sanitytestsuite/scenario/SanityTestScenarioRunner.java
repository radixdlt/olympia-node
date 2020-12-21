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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.radixdlt.sanitytestsuite.model.SanityTestSuiteRoot.Suite.Scenario;
import com.radixdlt.sanitytestsuite.model.SanityTestVector;
import com.radixdlt.utils.JSONFormatter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;

public abstract class SanityTestScenarioRunner<TestVector extends SanityTestVector<?, ?>> {
	private final ObjectMapper mapper = new ObjectMapper();

	public abstract String testScenarioIdentifier();
	public abstract Class<TestVector> testVectorType();

	public abstract void doRunTestVector(TestVector testVector) throws AssertionError;

	public void executeTest(final Scenario scenario) {
		assertEquals(testScenarioIdentifier(), scenario.identifier);
		var testVectorIndex = 0;

		for (var testVectorInput : scenario.tests.vectors) {
			var testVector = mapper.convertValue(testVectorInput, this.testVectorType());

			try {
				doRunTestVector(testVector);
				testVectorIndex++;
			} catch (AssertionError e) {
				String msg = String.format(
					"Failing test vector index: %d, vector: %s",
					testVectorIndex,
					JSONFormatter.sortPrettyPrintObject(testVector)
				);
				throw new AssertionError(msg, e);
			}
		}
	}

	public static byte[] sha256Hash(final byte[] bytes) {
		try {
			var hasher = MessageDigest.getInstance("SHA-256");
			hasher.update(bytes);
			return hasher.digest();
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError("Failed to run test, found no hasher", e);
		}
	}
}