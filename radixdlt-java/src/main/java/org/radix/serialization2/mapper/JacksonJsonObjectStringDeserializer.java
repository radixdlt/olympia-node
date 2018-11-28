package org.radix.serialization2.mapper;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Deserializer for translation from JSON encoded {@code String} data
 * to an string representable object.
 * @param <T> The type to deserialize
 */
class JacksonJsonObjectStringDeserializer<T> extends StdDeserializer<T> {
	private static final long serialVersionUID = 290L;

	private final Function<String, T> stringMapper;
	private final String prefix;

	JacksonJsonObjectStringDeserializer(Class<T> t, String prefix, Function<String, T> stringMapper) {
		super(t);
		this.prefix = Objects.requireNonNull(prefix);
		this.stringMapper = Objects.requireNonNull(stringMapper);
	}

	@Override
	public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String value = p.getValueAsString();
		if (!value.startsWith(prefix)) {
			throw new InvalidFormatException(p, "Expecting string " + prefix, value, this.handledType());
		}

		return stringMapper.apply(value.substring(JacksonCodecConstants.STR_VALUE_LEN));
	}
}
