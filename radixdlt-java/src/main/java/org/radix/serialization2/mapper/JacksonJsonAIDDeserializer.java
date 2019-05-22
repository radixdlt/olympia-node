package org.radix.serialization2.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.radix.common.ID.AID;

import java.io.IOException;

/**
 * Deserializer for translation from JSON encoded {@code EUID} data
 * to an {@code EUID} object.
 */
class JacksonJsonAIDDeserializer extends StdDeserializer<AID> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonJsonAIDDeserializer() {
		this(null);
	}

	JacksonJsonAIDDeserializer(Class<AID> t) {
		super(t);
	}

	@Override
	public AID deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		String value = p.getValueAsString();
		if (!value.startsWith(JacksonCodecConstants.AID_STR_VALUE)) {
			throw new InvalidFormatException(p, "Expecting AID", value, AID.class);
		}
		return AID.from(value.substring(JacksonCodecConstants.STR_VALUE_LEN));
	}
}
