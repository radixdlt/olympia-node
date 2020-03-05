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
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Serializer for conversion from an object representable by a string
 * to the appropriate JSON encoding.
 */
public class JacksonJsonObjectStringSerializer<T> extends StdSerializer<T> {
	private static final long serialVersionUID = -4231287848387995937L;
	private final String prefix;
	private final Function<T, String> toStringMapper;

	JacksonJsonObjectStringSerializer(Class<T> t, String prefix, Function<T, String> toStringMapper) {
		super(t);
		this.prefix = Objects.requireNonNull(prefix);
		this.toStringMapper = Objects.requireNonNull(toStringMapper);
	}

	@Override
	public void serialize(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
		jgen.writeString(prefix + toStringMapper.apply(value));
	}
}
