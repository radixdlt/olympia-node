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
