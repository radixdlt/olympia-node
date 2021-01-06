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

package com.radixdlt;

import com.radixdlt.utils.JSONFormatter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JSONFormatterTest {

	@Test
	public void test_sort_and_pretty_print_json() {
		String jsonString = "{\n"
			+ "    \"hid\": \":uid:85561567e9dc96578362806ce9e136f2\",\n"
			+ "    \"destinations\": [\n"
			+ "        \":uid:dfd7c486570a7ad40eb948c80cb89376\"\n"
			+ "    ],\n"
			+ "    \"rri\": \":rri:/JFLKeSQmBZ73YkzWiesdEr2fRT14qCB1DQUvj8KxYQC6m8UTCcF/XRD\",\n"
			+ "    \"serializer\": \"radix.particles.rri\",\n"
			+ "    \"version\": 100,\n"
			+ "    \"nonce\": 0\n"
			+ "}";


		String jsonStringManuallySorted = "{\n"
		  + "    \"destinations\": [\n"
		  + "        \":uid:dfd7c486570a7ad40eb948c80cb89376\"\n"
		  + "    ],\n"
		  + "    \"hid\": \":uid:85561567e9dc96578362806ce9e136f2\",\n"
		  + "    \"nonce\": 0,\n"
		  + "    \"rri\": \":rri:/JFLKeSQmBZ73YkzWiesdEr2fRT14qCB1DQUvj8KxYQC6m8UTCcF/XRD\",\n"
		  + "    \"serializer\": \"radix.particles.rri\",\n"
		  + "    \"version\": 100\n"
		  + "}";

		String jsonStringSorted = JSONFormatter.sortPrettyPrintJSONString(jsonString);

		assertEquals(jsonStringManuallySorted, jsonStringSorted);

	}

}
