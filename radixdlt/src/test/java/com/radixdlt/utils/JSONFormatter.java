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
			String jsonStringSorted = mapper
					.writer(new MyPrettyPrinter())
					.writeValueAsString(jsonSorted);

			return jsonStringSorted;
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		throw new IllegalStateException("Failed to pretty print JSON string");
	}
}
