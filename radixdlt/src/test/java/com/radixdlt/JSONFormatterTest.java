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
