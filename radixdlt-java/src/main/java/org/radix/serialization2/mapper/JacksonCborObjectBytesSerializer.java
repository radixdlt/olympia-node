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
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

class JacksonCborObjectBytesSerializer<T> extends StdSerializer<T> {
	private static final long serialVersionUID = 290L;

	private final byte prefix;
	private final Function<T, byte[]> toByteArrayMapper;

	JacksonCborObjectBytesSerializer(byte prefix, Function<T, byte[]> toByteArrayMapper) {
		this(null, prefix, toByteArrayMapper);
	}

	JacksonCborObjectBytesSerializer(Class<T> t, byte prefix, Function<T, byte[]> toByteArrayMapper) {
		super(t);
		this.prefix = prefix;
		this.toByteArrayMapper = toByteArrayMapper;
	}

	@Override
	public void serialize(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
		byte[] objectBytes = toByteArrayMapper.apply(value);
		byte[] bytes = new byte[1 + objectBytes.length];

		bytes[0] = prefix;
		System.arraycopy(objectBytes, 0, bytes, 1, objectBytes.length);
		jgen.writeBinary(bytes);
	}
}
