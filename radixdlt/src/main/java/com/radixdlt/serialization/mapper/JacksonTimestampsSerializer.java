package com.radixdlt.serialization.mapper;

import java.io.IOException;
import java.util.Map;

import org.radix.time.Timestamps;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Serializer for conversion from DSON {@code Hash} data
 * to CBOR binary data.
 */
class JacksonTimestampsSerializer extends StdSerializer<Timestamps> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonTimestampsSerializer() {
		this(null);
	}

	JacksonTimestampsSerializer(Class<Timestamps> t) {
		super(t);
	}

	@Override
	public void serialize(Timestamps value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
		jgen.writeStartObject(value);
		for (Map.Entry<String, Long> e : value.entrySet()) {
			jgen.writeNumberField(e.getKey(), e.getValue().longValue());
		}
		jgen.writeEndObject();
	}
}