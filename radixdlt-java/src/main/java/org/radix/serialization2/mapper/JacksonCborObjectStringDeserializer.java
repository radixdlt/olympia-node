package org.radix.serialization2.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;
import org.radix.utils.RadixConstants;

public class JacksonCborObjectStringDeserializer<T> extends StdDeserializer<T> {
	private final byte prefix;
	private final Function<String, T> stringMapper;

	JacksonCborObjectStringDeserializer(Class<T> t, byte prefix, Function<String, T> stringMapper) {
		super(t);
		this.prefix = prefix;
		this.stringMapper = stringMapper;
	}

	@Override
	public T deserialize(JsonParser p, DeserializationContext ctxt)
		throws IOException, JsonProcessingException {
		byte[] bytes = p.getBinaryValue();
		if (bytes == null || bytes.length == 0 || bytes[0] != prefix) {
			throw new InvalidFormatException(p, "Expecting " + prefix, bytes, this.handledType());
		}
		String s = new String(Arrays.copyOfRange(bytes, 1, bytes.length), RadixConstants.STANDARD_CHARSET);
		return stringMapper.apply(s);
	}

}
