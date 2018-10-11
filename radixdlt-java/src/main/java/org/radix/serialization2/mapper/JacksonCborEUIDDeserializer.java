package org.radix.serialization2.mapper;

import java.io.IOException;
import java.util.Arrays;

import org.radix.common.ID.EUID;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Deserializer for translation from CBOR binary data
 * to DSON {@code EUID} data.
 */
class JacksonCborEUIDDeserializer extends StdDeserializer<EUID> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonCborEUIDDeserializer() {
		this(null);
	}

	JacksonCborEUIDDeserializer(Class<EUID> t) {
		super(t);
	}

	@Override
	public EUID deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		byte[] bytes = p.getBinaryValue();
		if (bytes == null || bytes.length == 0 || bytes[0] != JacksonCodecConstants.EUID_VALUE) {
			throw new InvalidFormatException(p, "Expecting EUID", bytes, EUID.class);
		}
		return new EUID(Arrays.copyOfRange(bytes, 1, bytes.length));
	}
}