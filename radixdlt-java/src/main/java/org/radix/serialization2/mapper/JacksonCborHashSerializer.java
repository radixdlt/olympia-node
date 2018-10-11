package org.radix.serialization2.mapper;

import java.io.IOException;

import org.radix.crypto.Hash;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Serializer for conversion from DSON {@code Hash} data
 * to CBOR binary data.
 */
class JacksonCborHashSerializer extends StdSerializer<Hash> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonCborHashSerializer() {
		this(null);
	}

	JacksonCborHashSerializer(Class<Hash> t) {
		super(t);
	}

	@Override
	public void serialize(Hash value, JsonGenerator jgen, SerializerProvider provider)
			throws IOException, JsonProcessingException {
		byte[] bytes = new byte[1 + Hash.BYTES];
		bytes[0] = JacksonCodecConstants.HASH_VALUE;
		System.arraycopy(value.toByteArray(), 0, bytes, 1, Hash.BYTES);
		jgen.writeBinary(bytes);
	}
}