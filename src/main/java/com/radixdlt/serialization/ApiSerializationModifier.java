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

package com.radixdlt.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.serialization.mapper.JacksonCborMapper;

import java.io.IOException;

/**
 * A Jackson bean serialization modifier that handles adding the "hid" property to output data
 * for classes annotated with @SerializeWithHid annotation.
 */
public class ApiSerializationModifier extends BeanSerializerModifier {

    private final JacksonCborMapper hashDsonMapper;

    public ApiSerializationModifier(JacksonCborMapper hashDsonMapper) {
        this.hashDsonMapper = hashDsonMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public JsonSerializer<?> modifySerializer(
            final SerializationConfig serializationConfig,
            final BeanDescription beanDescription,
            final JsonSerializer<?> jsonSerializer) {
        return new SerializerWithHid((JsonSerializer<Object>) jsonSerializer);
    }

    private class SerializerWithHid extends JsonSerializer<Object> {

        private final JsonSerializer<Object> serializer;

        SerializerWithHid(JsonSerializer<Object> jsonSerializer) {
            this.serializer = jsonSerializer;
        }

        @Override
        public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            if (o.getClass().isAnnotationPresent(SerializeWithHid.class)) {
                jsonGenerator.writeStartObject();
                serializer.unwrappingSerializer(null).serialize(o, jsonGenerator, serializerProvider);
                byte[] bytesToHash = hashDsonMapper.writeValueAsBytes(o);
                HashCode hash = HashUtils.sha256(bytesToHash);
                jsonGenerator.writeObjectField("hid", hash);
                jsonGenerator.writeEndObject();
            } else {
                serializer.serialize(o, jsonGenerator, serializerProvider);
            }
        }
    }
}
