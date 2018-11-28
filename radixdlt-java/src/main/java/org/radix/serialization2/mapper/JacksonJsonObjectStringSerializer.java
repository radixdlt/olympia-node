package org.radix.serialization2.mapper;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Serializer for conversion from an object representable by a string
 * to the appropriate JSON encoding.
 * @param <T> The type to serialize
 */
class JacksonJsonObjectStringSerializer<T> extends StdSerializer<T> {
	private static final long serialVersionUID = 290L;

	private final String prefix;
	private final Function<T, String> toStringMapper;

	JacksonJsonObjectStringSerializer(String prefix, Function<T, String> toStringMapper) {
		this(null, prefix, toStringMapper);
	}

	JacksonJsonObjectStringSerializer(Class<T> t, String prefix, Function<T, String> toStringMapper) {
		super(t);
		this.prefix = Objects.requireNonNull(prefix);
		this.toStringMapper = Objects.requireNonNull(toStringMapper);
	}

	@Override
	public void serialize(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
		jgen.writeString(prefix + toStringMapper.apply(value));
	}
}
