package org.radix.serialization2.mapper;

import java.io.IOException;

import org.radix.common.ID.EUID;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Deserializer for translation from JSON encoded {@code EUID} data
 * to an {@code EUID} object.
 */
class JacksonJsonEUIDDeserializer extends StdDeserializer<EUID> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonJsonEUIDDeserializer() {
		this(null);
	}

	JacksonJsonEUIDDeserializer(Class<EUID> t) {
		super(t);
	}

	@Override
	public EUID deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		String value = p.getValueAsString();
		if (!value.startsWith(JacksonCodecConstants.EUID_STR_VALUE))
			throw new InvalidFormatException(p, "Expecting UID", value, EUID.class);
		return EUID.valueOf(value.substring(JacksonCodecConstants.STR_VALUE_LEN));
	}
}
