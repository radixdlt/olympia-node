package org.radix.serialization2.mapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.function.Function;
import org.radix.utils.RadixConstants;

public class JacksonCborObjectStringSerializer<T> extends StdSerializer<T> {

	private final byte prefix;
	private final Function<T, String> toStringMapper;

	JacksonCborObjectStringSerializer(byte prefix, Function<T, String> toStringMapper) {
		this(null, prefix, toStringMapper);
	}

	JacksonCborObjectStringSerializer(Class<T> t, byte prefix, Function<T, String> toStringMapper) {
		super(t);
		this.prefix = prefix;
		this.toStringMapper = toStringMapper;
	}

	@Override
	public void serialize(T value, JsonGenerator jgen, SerializerProvider provider)
		throws IOException, JsonProcessingException {

		// TODO: Could optimize this to get rid of construction of stringBytes array
		String s = toStringMapper.apply(value);
		byte[] stringBytes = s.getBytes(RadixConstants.STANDARD_CHARSET);
		byte[] bytes = new byte[1 + stringBytes.length];

		bytes[0] = prefix;
		System.arraycopy(stringBytes, 0, bytes, 1, stringBytes.length);
		jgen.writeBinary(bytes);
	}
}
