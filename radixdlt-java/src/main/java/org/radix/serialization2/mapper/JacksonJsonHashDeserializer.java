package org.radix.serialization2.mapper;

import java.io.IOException;

import org.radix.crypto.Hash;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Deserializer for translation from JSON encoded {@code Hash} data
 * to a {@code Hash} object.
 */
class JacksonJsonHashDeserializer extends StdDeserializer<Hash> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonJsonHashDeserializer() {
		this(null);
	}

	JacksonJsonHashDeserializer(Class<Hash> t) {
		super(t);
	}

	@Override
	public Hash deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		String value = p.getValueAsString();
		if (!value.startsWith(JacksonCodecConstants.HASH_STR_VALUE)) {
			throw new InvalidFormatException(p, "Expecting Hash", value, Hash.class);
		}
		return new Hash(value.substring(JacksonCodecConstants.STR_VALUE_LEN));
	}
}
