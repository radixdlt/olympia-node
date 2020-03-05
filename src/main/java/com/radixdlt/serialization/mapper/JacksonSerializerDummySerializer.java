/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.serialization.mapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerIds;
import java.io.IOException;

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
	public void serialize(SerializerDummy value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
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