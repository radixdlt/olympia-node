package org.radix.serialization2.mapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.function.Function;

public class JacksonJsonObjectStringSerializer<T> extends StdSerializer<T> {
	private final String prefix;
	private final Function<T, String> toStringMapper;

	JacksonJsonObjectStringSerializer(String prefix, Function<T, String> toStringMapper) {
		this(null, prefix, toStringMapper);
	}

	JacksonJsonObjectStringSerializer(Class<T> t, String prefix, Function<T, String> toStringMapper) {
		super(t);
		this.prefix = prefix;
		this.toStringMapper = toStringMapper;
	}

	@Override
	public void serialize(T value, JsonGenerator jgen, SerializerProvider provider)
		throws IOException, JsonProcessingException {
		jgen.writeString(prefix + toStringMapper.apply(value));
	}
}
