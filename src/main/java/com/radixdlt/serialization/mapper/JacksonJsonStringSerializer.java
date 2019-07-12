package com.radixdlt.serialization.mapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

/**
 * Serializer for conversion from {@code String} data
 * to the appropriate JSON encoding.
 */
class JacksonJsonStringSerializer extends StdSerializer<String> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonJsonStringSerializer() {
		this(null);
	}

	JacksonJsonStringSerializer(Class<String> t) {
		super(t);
	}

	@Override
	public void serialize(String value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
		jgen.writeString(JacksonCodecConstants.STR_STR_VALUE + value);
	}
}
