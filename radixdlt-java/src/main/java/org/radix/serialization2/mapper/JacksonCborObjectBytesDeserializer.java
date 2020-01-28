/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package org.radix.serialization2.mapper;

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
class JacksonCborObjectBytesDeserializer<T> extends StdDeserializer<T> {
	private static final long serialVersionUID = 290L;

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
