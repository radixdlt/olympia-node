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

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.radixdlt.sanitytestsuite.model.SanityTestSuiteRoot;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.JSONFormatter;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import static com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner.sha256Hash;
import static org.junit.Assert.assertEquals;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public final class SanityTestSuiteTestLoader {


	public static Gson gson = new GsonBuilder()
			.registerTypeAdapter(Double.class, new SanityTestSuiteTestLoader.DoubleSerializer())
			.create();

	public static class DoubleSerializer implements JsonSerializer<Double> {
		@Override
		public JsonElement serialize(Double src, Type typeOfSrc, JsonSerializationContext context) {
			return src == src.longValue() ? new JsonPrimitive(src.longValue()) : new JsonPrimitive(src);
		}
	}


	public SanityTestSuiteRoot sanityTestSuiteRootFromFileNamed(String sanityTestJSONFileName) {

		SanityTestSuiteRoot sanityTestSuiteRoot = null;
		try {
			ClassLoader classLoader = getClass().getClassLoader();
			File file = new File(classLoader.getResource(sanityTestJSONFileName).getFile());

			// Compare saved hash in file with calculated hash of test.
			String jsonFileContent = Files.asCharSource(file, StandardCharsets.UTF_8).read();
			JSONObject sanityTestSuiteRootAsJsonObject = new JSONObject(jsonFileContent);



			String prettyPrintedSorted = JSONFormatter.sortPrettyPrintJSONString(sanityTestSuiteRootAsJsonObject.getJSONObject("suite").toString());

			sanityTestSuiteRoot = gson.fromJson(jsonFileContent, SanityTestSuiteRoot.class);

			String sanityTestSuiteSavedHash = sanityTestSuiteRoot.hashOfSuite;
			byte[] suiteBytes = prettyPrintedSorted.getBytes(StandardCharsets.UTF_8);
			byte[] calculatedHashOfSanityTestSuite = sha256Hash(suiteBytes);
			assertEquals(sanityTestSuiteSavedHash, Bytes.toHexString(calculatedHashOfSanityTestSuite));
		} catch (IOException e) {
			throw new IllegalStateException("failed to load test vectors, e: " + e);
		}



		return sanityTestSuiteRoot;

	}
}

// CHECKSTYLE:ON checkstyle:VisibilityModifier
