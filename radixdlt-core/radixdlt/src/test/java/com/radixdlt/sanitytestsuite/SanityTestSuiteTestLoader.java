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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;
import com.radixdlt.sanitytestsuite.model.SanityTestSuiteRoot;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.JSONFormatter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

import static com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner.sha256Hash;

public final class SanityTestSuiteTestLoader {
	private final ObjectMapper mapper = new ObjectMapper();

	public SanityTestSuiteRoot sanityTestSuiteRootFromFileNamed(String sanityTestJSONFileName) {
		try {
			var sanityTestSuiteRoot =	readTestSuiteContent(sanityTestJSONFileName);
			var calculated = calculateSuiteHash(sanityTestSuiteRoot);
			var expected = sanityTestSuiteRoot.integrity.hashOfSuite;

			// Compare saved hash in file with calculated hash of test.
			assertEquals(prepareMessage(sanityTestSuiteRoot), expected, calculated);

			return sanityTestSuiteRoot;
		} catch (IOException e) {
			throw new IllegalStateException("failed to sanity test suite", e);
		}
	}

	private String prepareMessage(SanityTestSuiteRoot sanityTest) {
		return String.format(
			"Mismatch between calculated hash of test suite and expected (bundled hash), implementation info: %s",
			sanityTest.integrity.implementationInfo
		);
	}

	private String calculateSuiteHash(SanityTestSuiteRoot sanityTest) {
		final var suiteStringRaw = JSONFormatter.sortPrettyPrintObject(sanityTest.suite);
		final var suiteString = suiteStringRaw.replace("\r\n", "\n"); // Fix CRLF line ends
		final var suiteBytes = suiteString.getBytes(StandardCharsets.UTF_8);
		return Bytes.toHexString(sha256Hash(suiteBytes));
	}

	private SanityTestSuiteRoot readTestSuiteContent(String sanityTestJSONFileName) throws IOException {
		var resource = getClass().getClassLoader().getResource(sanityTestJSONFileName);
		File file = new File(resource.getFile());

		var jsonFileContent = Files.asCharSource(file, StandardCharsets.UTF_8).read();
		return mapper.readValue(jsonFileContent, SanityTestSuiteRoot.class);
	}
}
