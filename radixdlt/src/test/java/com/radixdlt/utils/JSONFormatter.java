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

package com.radixdlt.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.TreeMap;

public class JSONFormatter {

	private JSONFormatter() {
		throw new UnsupportedOperationException("Cannot instantiate.");
	}

	static class SortingNodeFactory extends JsonNodeFactory {
		@Override
		public ObjectNode objectNode() {
			return new ObjectNode(this, new TreeMap<String, JsonNode>());
		}
	}

	static class MyPrettyPrinter extends DefaultPrettyPrinter {

		@Override
		public DefaultPrettyPrinter createInstance() {
			MyPrettyPrinter printer = new MyPrettyPrinter();

			DefaultPrettyPrinter.Indenter indenter =
					new DefaultIndenter("    ", DefaultIndenter.SYS_LF);

			printer.indentObjectsWith(indenter);
			printer.indentArraysWith(indenter);

			return printer;
		}

		@Override
		public void writeObjectFieldValueSeparator(JsonGenerator jg) throws IOException {
			jg.writeRaw(": ");
		}

	}

	public static String sortPrettyPrintObject(Object object) {
		ObjectMapper mapper = new ObjectMapper();
		String jsonString = null;
		try {
			jsonString = mapper.writeValueAsString(object);
			return sortPrettyPrintJSONString(jsonString);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		throw new IllegalStateException("Failed to pretty print object to JSON string");
	}

	public static String sortPrettyPrintJSONString(String uglyJson) {
		ObjectMapper mapper = JsonMapper.builder()
				.nodeFactory(new SortingNodeFactory())
				.build();

		JsonNode jsonSorted = null;
		try {
			jsonSorted = mapper.readTree(uglyJson);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		try {
			return mapper
				.writer(new MyPrettyPrinter())
				.writeValueAsString(jsonSorted);

		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		throw new IllegalStateException("Failed to pretty print JSON string");
	}
}
