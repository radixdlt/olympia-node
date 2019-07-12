package com.radixdlt.serialization.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.radixdlt.crypto.Hash;
import java.io.IOException;

/**
 * Deserializer for translation from JSON encoded {@code String} data
 * to a {@code String} object.
 */
class JacksonJsonStringDeserializer extends StdDeserializer<String> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonJsonStringDeserializer() {
		this(null);
	}

	JacksonJsonStringDeserializer(Class<String> t) {
		super(t);
	}

	@Override
	public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String value = p.getValueAsString();
		if (!value.startsWith(JacksonCodecConstants.STR_STR_VALUE))
			throw new InvalidFormatException(p, "Expecting string", value, Hash.class);
		return value.substring(JacksonCodecConstants.STR_VALUE_LEN);
	}
}
