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

import org.radix.common.ID.EUID;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Serializer for conversion from DSON {@code EUID} data
 * to CBOR binary data.
 */
class JacksonCborEUIDSerializer extends StdSerializer<EUID> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonCborEUIDSerializer() {
		this(null);
	}

	JacksonCborEUIDSerializer(Class<EUID> t) {
		super(t);
	}

	@Override
	public void serialize(EUID value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
		byte[] bytes = new byte[1 + EUID.BYTES];
		bytes[0] = JacksonCodecConstants.EUID_VALUE;
		value.toByteArray(bytes, 1);
		jgen.writeBinary(bytes);
	}
}