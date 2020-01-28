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

import org.radix.serialization2.SerializerDummy;
import org.radix.serialization2.SerializerIds;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Special converter for {@link SerializerDummy} class.
 */
class JacksonSerializerDummySerializer extends StdSerializer<SerializerDummy> {
	private static final long serialVersionUID = -2472482347700365657L;
	private final SerializerIds idLookup;

	JacksonSerializerDummySerializer(SerializerIds idLookup) {
		this(null, idLookup);
	}

	JacksonSerializerDummySerializer(Class<SerializerDummy> t, SerializerIds idLookup) {
		super(t);
		this.idLookup = idLookup;
	}

	@Override
	public void serialize(SerializerDummy value, JsonGenerator jgen, SerializerProvider provider)
			throws IOException, JsonProcessingException {
		Object parent = jgen.getOutputContext().getCurrentValue();
		String id = idLookup.getIdForClass(parent.getClass());
		if (id == null) {
			throw new IllegalStateException("Can't find ID for class: " + parent.getClass().getName());
		}
		jgen.writeString(id);
	}

    @Override
	public void serializeWithType(SerializerDummy value, JsonGenerator jgen, SerializerProvider provider, TypeSerializer typeSer)
			throws IOException {
    	serialize(value, jgen, provider);
    }
}