package org.radix.serialization2.mapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.radix.common.ID.AID;

import java.io.IOException;

/**
 * Serializer for conversion from DSON {@code AID} data
 * to CBOR binary data.
 */
class JacksonCborAIDSerializer extends StdSerializer<AID> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonCborAIDSerializer() {
		this(null);
	}

	JacksonCborAIDSerializer(Class<AID> t) {
		super(t);
	}

	@Override
	public void serialize(AID value, JsonGenerator jgen, SerializerProvider provider)
			throws IOException, JsonProcessingException {
		byte[] bytes = new byte[1 + AID.BYTES];
		bytes[0] = JacksonCodecConstants.AID_VALUE;
		value.copyTo(bytes, 1);
		jgen.writeBinary(bytes);
	}
}