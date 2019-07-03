package com.radixdlt.serialization.mapper;

import java.io.IOException;

import com.radixdlt.utils.Bytes;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Deserializer for translation from JSON encoded binary data
 * to {@code byte[]} data.
 */
class JacksonJsonBytesDeserializer extends StdDeserializer<byte[]> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonJsonBytesDeserializer() {
		this(null);
	}

	JacksonJsonBytesDeserializer(Class<byte[]> t) {
		super(t);
	}

	@Override
	public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String value = p.getValueAsString();
		if (!value.startsWith(JacksonCodecConstants.BYTE_STR_VALUE))
			throw new InvalidFormatException(p, "Expecting bytes", value, byte[].class);
		return Bytes.fromBase64String(value.substring(JacksonCodecConstants.STR_VALUE_LEN));
	}
}
