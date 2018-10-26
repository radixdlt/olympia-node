package org.radix.serialization2.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.util.function.Function;

public class JacksonJsonObjectStringDeserializer<T> extends StdDeserializer<T> {
	private final Function<String, T> stringMapper;
	private final String prefix;

	JacksonJsonObjectStringDeserializer(Class<T> t, String prefix, Function<String, T> stringMapper) {
		super(t);
		this.prefix = prefix;
		this.stringMapper = stringMapper;
	}

	@Override
	public T deserialize(JsonParser p, DeserializationContext ctxt)
		throws IOException, JsonProcessingException {
		String value = p.getValueAsString();
		if (!value.startsWith(prefix)) {
			throw new InvalidFormatException(p, "Expecting string " + prefix, value, this.handledType());
		}

		return stringMapper.apply(value.substring(JacksonCodecConstants.STR_VALUE_LEN));
	}
}
