package org.radix.serialization2.mapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.radix.common.ID.AID;

import java.io.IOException;

/**
 * Serializer for conversion from {@code AID} data
 * to the appropriate JSON encoding.
 */
class JacksonJsonAIDSerializer extends StdSerializer<AID> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonJsonAIDSerializer() {
		this(null);
	}

	JacksonJsonAIDSerializer(Class<AID> t) {
		super(t);
	}

	@Override
	public void serialize(AID value, JsonGenerator jgen, SerializerProvider provider)
			throws IOException, JsonProcessingException {
		jgen.writeString(JacksonCodecConstants.AID_STR_VALUE + value.toString());
	}
}
