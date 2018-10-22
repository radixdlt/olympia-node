package org.radix.serialization2.mapper;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Serializer for conversion from DSON {@code byte[]} data
 * to CBOR binary data.
 */
class JacksonCborBytesSerializer extends StdSerializer<byte[]> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonCborBytesSerializer() {
		this(null);
	}

	JacksonCborBytesSerializer(Class<byte[]> t) {
		super(t);
	}

	@Override
	public void serialize(byte[] value, JsonGenerator jgen, SerializerProvider provider)
			throws IOException, JsonProcessingException {
		byte[] otherBytes = new byte[value.length + 1];
		otherBytes[0] = JacksonCodecConstants.BYTES_VALUE;
		System.arraycopy(value, 0, otherBytes, 1, value.length);
		jgen.writeBinary(otherBytes);
	}
}