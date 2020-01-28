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

import org.radix.crypto.Hash;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Deserializer for translation from JSON encoded {@code Hash} data
 * to a {@code Hash} object.
 */
class JacksonJsonHashDeserializer extends StdDeserializer<Hash> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonJsonHashDeserializer() {
		this(null);
	}

	JacksonJsonHashDeserializer(Class<Hash> t) {
		super(t);
	}

	@Override
	public Hash deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		String value = p.getValueAsString();
		if (!value.startsWith(JacksonCodecConstants.HASH_STR_VALUE)) {
			throw new InvalidFormatException(p, "Expecting Hash", value, Hash.class);
		}
		return new Hash(value.substring(JacksonCodecConstants.STR_VALUE_LEN));
	}
}
