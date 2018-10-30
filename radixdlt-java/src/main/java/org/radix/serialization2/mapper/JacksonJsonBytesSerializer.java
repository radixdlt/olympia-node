package org.radix.serialization2.mapper;

import java.io.IOException;

import org.radix.utils.primitives.Bytes;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Serializer for conversion from {@code byte[]} data
 * to the appropriate JSON encoding.
 */
class JacksonJsonBytesSerializer extends StdSerializer<byte[]> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonJsonBytesSerializer() {
		this(null);
	}

	JacksonJsonBytesSerializer(Class<byte[]> t) {
		super(t);
	}

	@Override
	public void serialize(byte[] value, JsonGenerator jgen, SerializerProvider provider)
			throws IOException, JsonProcessingException {
		jgen.writeString(JacksonCodecConstants.BYTE_STR_VALUE + Bytes.toBase64String(value));
	}
}
