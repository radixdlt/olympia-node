package com.radixdlt.serialization.mapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Deserializer for translation from CBOR binary data
 * to DSON data for an object which can be represented by a string.
 */
public class JacksonCborObjectBytesDeserializer<T> extends StdDeserializer<T> {
	private final byte prefix;
	private final Function<byte[], T> bytesMapper;

	JacksonCborObjectBytesDeserializer(Class<T> t, byte prefix, Function<byte[], T> bytesMapper) {
		super(t);
		this.prefix = prefix;
		this.bytesMapper = bytesMapper;
	}

	@Override
	public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		byte[] bytes = p.getBinaryValue();
		if (bytes == null || bytes.length == 0 || bytes[0] != prefix) {
			throw new InvalidFormatException(p, "Expecting " + prefix, bytes, this.handledType());
		}
		return bytesMapper.apply(Arrays.copyOfRange(bytes, 1, bytes.length));
	}

}
