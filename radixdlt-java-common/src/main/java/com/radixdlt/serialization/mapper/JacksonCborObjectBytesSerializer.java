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
import java.util.function.Function;

public class JacksonCborObjectBytesSerializer<T> extends StdSerializer<T> {

	private final byte prefix;
	private final Function<T, byte[]> toByteArrayMapper;

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
