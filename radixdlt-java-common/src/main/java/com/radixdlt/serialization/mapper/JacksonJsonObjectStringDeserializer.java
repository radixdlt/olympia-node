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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Deserializer for translation from JSON encoded {@code String} data
 * to an string representable object.
 */
public class JacksonJsonObjectStringDeserializer<T> extends StdDeserializer<T> {
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
