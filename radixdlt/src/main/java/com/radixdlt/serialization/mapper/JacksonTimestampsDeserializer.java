package com.radixdlt.serialization.mapper;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.radix.time.Timestamps;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * Deserializer for translation from CBOR binary data
 * to DSON {@code Hash} data.
 */
class JacksonTimestampsDeserializer extends StdDeserializer<Timestamps> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonTimestampsDeserializer() {
		this(null);
	}

	JacksonTimestampsDeserializer(Class<Timestamps> t) {
		super(t);
	}

	@Override
	public Timestamps deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		ObjectCodec oc = p.getCodec();
		JsonNode node = oc.readTree(p);
		Iterator<Map.Entry<String, JsonNode>> i = node.fields();
		Timestamps timestamps = new Timestamps();
		while (i.hasNext()) {
			Map.Entry<String, JsonNode> e = i.next();
			timestamps.put(e.getKey(), e.getValue().asLong());
		}
		return timestamps;
	}
}