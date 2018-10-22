package org.radix.serialization2.mapper;

import java.io.IOException;
import java.util.Arrays;

import org.radix.crypto.Hash;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Deserializer for translation from CBOR binary data
 * to DSON {@code Hash} data.
 */
class JacksonCborHashDeserializer extends StdDeserializer<Hash> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonCborHashDeserializer() {
		this(null);
	}

	JacksonCborHashDeserializer(Class<Hash> t) {
		super(t);
	}

	@Override
	public Hash deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		byte[] bytes = p.getBinaryValue();
		if (bytes == null || bytes.length == 0 || bytes[0] != JacksonCodecConstants.HASH_VALUE) {
			throw new InvalidFormatException(p, "Expecting hash", bytes, Hash.class);
		}
		return new Hash(Arrays.copyOfRange(bytes, 1, bytes.length));
	}
}