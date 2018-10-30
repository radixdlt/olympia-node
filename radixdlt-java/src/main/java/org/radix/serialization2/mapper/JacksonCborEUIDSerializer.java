package org.radix.serialization2.mapper;

import java.io.IOException;

import org.radix.common.ID.EUID;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Serializer for conversion from DSON {@code EUID} data
 * to CBOR binary data.
 */
class JacksonCborEUIDSerializer extends StdSerializer<EUID> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonCborEUIDSerializer() {
		this(null);
	}

	JacksonCborEUIDSerializer(Class<EUID> t) {
		super(t);
	}

	@Override
	public void serialize(EUID value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
		byte[] bytes = new byte[1 + EUID.BYTES];
		bytes[0] = JacksonCodecConstants.EUID_VALUE;
		value.toByteArray(bytes, 1);
		jgen.writeBinary(bytes);
	}
}