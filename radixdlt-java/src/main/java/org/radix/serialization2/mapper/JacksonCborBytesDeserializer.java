package org.radix.serialization2.mapper;

import java.io.IOException;
import java.util.Arrays;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Deserializer for translation from CBOR binary data
 * to DSON {@code byte[]} data.
 */
class JacksonCborBytesDeserializer extends StdDeserializer<byte[]> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonCborBytesDeserializer() {
		this(null);
	}

	JacksonCborBytesDeserializer(Class<byte[]> t) {
		super(t);
	}

	@Override
	public byte[] deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		byte[] bytes = p.getBinaryValue();
		if (bytes == null || bytes.length == 0 || bytes[0] != JacksonCodecConstants.BYTES_VALUE) {
			throw new InvalidFormatException(p, "Expecting bytes", bytes, byte[].class);
		}
		return Arrays.copyOfRange(bytes, 1, bytes.length);
	}
}