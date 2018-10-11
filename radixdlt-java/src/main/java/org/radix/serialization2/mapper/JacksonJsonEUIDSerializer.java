package org.radix.serialization2.mapper;

import java.io.IOException;

import org.radix.common.ID.EUID;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Serializer for conversion from {@code EUID} data
 * to the appropriate JSON encoding.
 */
class JacksonJsonEUIDSerializer extends StdSerializer<EUID> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonJsonEUIDSerializer() {
		this(null);
	}

	JacksonJsonEUIDSerializer(Class<EUID> t) {
		super(t);
	}

	@Override
	public void serialize(EUID value, JsonGenerator jgen, SerializerProvider provider)
			throws IOException, JsonProcessingException {
		jgen.writeString(JacksonCodecConstants.EUID_STR_VALUE + value.toString());
	}
}
