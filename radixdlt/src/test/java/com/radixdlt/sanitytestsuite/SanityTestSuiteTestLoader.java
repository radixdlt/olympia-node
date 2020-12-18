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

import static com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner.sha256Hash;
import static org.junit.Assert.assertEquals;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public final class SanityTestSuiteTestLoader {

	public SanityTestSuiteRoot sanityTestSuiteRootFromFileNamed(String sanityTestJSONFileName) {

		try {
			ClassLoader classLoader = getClass().getClassLoader();
			File file = new File(classLoader.getResource(sanityTestJSONFileName).getFile());

			String jsonFileContent = Files.asCharSource(file, StandardCharsets.UTF_8).read();
			ObjectMapper mapper = new ObjectMapper();
			SanityTestSuiteRoot sanityTestSuiteRoot = mapper.readValue(jsonFileContent, SanityTestSuiteRoot.class);

			String prettyPrintedSorted = JSONFormatter.sortPrettyPrintObject(sanityTestSuiteRoot.suite);

			byte[] suiteBytes = prettyPrintedSorted.getBytes(StandardCharsets.UTF_8);
			byte[] calculatedHashOfSanityTestSuite = sha256Hash(suiteBytes);


			String calculated = Bytes.toHexString(calculatedHashOfSanityTestSuite);
			String expected = sanityTestSuiteRoot.integrity.hashOfSuite;

			// Compare saved hash in file with calculated hash of test.
//			assertEquals(
//				String.format("Mismatch between calculated hash of test suite and expected (bundled hash), implementation info: %s", sanityTestSuiteRoot.integrity.implementationInfo),
//				expected,
//				calculated
//			);

			return sanityTestSuiteRoot;

		} catch (IOException e) {
			throw new IllegalStateException("failed to sanity test suite, error: " + e);
		}
	}
}

// CHECKSTYLE:ON checkstyle:VisibilityModifier
