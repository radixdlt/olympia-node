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

/**
 * Deserializer for translation from JSON encoded {@code String} data
 * to a {@code String} object.
 */
class JacksonJsonStringDeserializer extends StdDeserializer<String> {
	private static final long serialVersionUID = -2472482347700365657L;

	JacksonJsonStringDeserializer() {
		this(null);
	}

	JacksonJsonStringDeserializer(Class<String> t) {
		super(t);
	}

	@Override
	public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String value = p.getValueAsString();
		if (!value.startsWith(JacksonCodecConstants.STR_STR_VALUE)) {
			throw new InvalidFormatException(p, "Expecting string", value, String.class);
		}
		return value.substring(JacksonCodecConstants.STR_VALUE_LEN);
	}
}
