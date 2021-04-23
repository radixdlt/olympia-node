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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.hash.HashCode;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerIds;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.util.Optional;

/**
 * A Jackson {@link RadixObjectMapperConfigurator} that will serialize and deserialize
 * to the JSON in the format that Radix requires.
 */
public class JacksonJsonMapper extends ObjectMapper {
	private static final long serialVersionUID = 4917479892309630214L;

	public static JacksonJsonMapper create(SerializerIds idLookup, FilterProvider filterProvider, boolean sortProperties) {
		return new JacksonJsonMapper(idLookup, filterProvider, sortProperties, Optional.empty());
	}

	/**
	 * Create an {@link RadixObjectMapperConfigurator} that will serialize to/from the JSON
	 * format that radix requires.
	 *
	 * @param idLookup A {@link SerializerIds} used to perform serializer
	 * 		ID lookup
	 * @param filterProvider A {@link FilterProvider} to use for filtering
	 * 		serialized fields
	 * @param sortProperties {@code true} if JSON output properties should be
	 * 		sorted in lexicographical order
	 * @param serializationModifier optional BeanSerializerModifier to mix in the serialization
	 * @return A freshly created {@link JacksonJsonMapper}
	 */
	public static JacksonJsonMapper create(SerializerIds idLookup, FilterProvider filterProvider, boolean sortProperties,
										   Optional<BeanSerializerModifier> serializationModifier) {
		return new JacksonJsonMapper(idLookup, filterProvider, sortProperties, serializationModifier);
	}

	private JacksonJsonMapper(SerializerIds idLookup, FilterProvider filterProvider, boolean sortProperties,
							  Optional<BeanSerializerModifier> serializationModifier) {
		super(new JsonFactory());
		RadixObjectMapperConfigurator.configure(this, idLookup, filterProvider, sortProperties);
		SimpleModule jsonModule = new SimpleModule();
		jsonModule.addSerializer(EUID.class, new JacksonJsonObjectStringSerializer<>(
				EUID.class,
				JacksonCodecConstants.EUID_STR_VALUE,
				EUID::toString
		));
		jsonModule.addSerializer(HashCode.class, new JacksonJsonHashCodeSerializer());
		jsonModule.addSerializer(byte[].class, new JacksonJsonBytesSerializer());
		jsonModule.addSerializer(String.class, new JacksonJsonStringSerializer());
		jsonModule.addSerializer(SerializerDummy.class, new JacksonSerializerDummySerializer(idLookup));
		jsonModule.addSerializer(UInt256.class, new JacksonJsonObjectStringSerializer<>(
				UInt256.class,
				JacksonCodecConstants.U20_STR_VALUE,
				UInt256::toString
		));
		jsonModule.addSerializer(UInt384.class, new JacksonJsonObjectStringSerializer<>(
				UInt384.class,
				JacksonCodecConstants.U30_STR_VALUE,
				UInt384::toString
		));
		jsonModule.addSerializer(REAddr.class, new JacksonJsonObjectStringSerializer<>(
				REAddr.class,
				JacksonCodecConstants.RRI_STR_VALUE,
				rri -> Hex.toHexString(rri.getBytes())
		));
		jsonModule.addSerializer(AID.class, new JacksonJsonObjectStringSerializer<>(
				AID.class,
				JacksonCodecConstants.AID_STR_VALUE,
				AID::toString
		));

		jsonModule.addKeySerializer(AID.class, new StdSerializer<AID>(AID.class) {
			@Override
			public void serialize(AID value, JsonGenerator gen, SerializerProvider provider) throws IOException {
				gen.writeFieldName(JacksonCodecConstants.AID_STR_VALUE + value.toString());
			}
		});

		jsonModule.addDeserializer(EUID.class, new JacksonJsonObjectStringDeserializer<>(
				EUID.class,
				JacksonCodecConstants.EUID_STR_VALUE,
				EUID::new
		));
		jsonModule.addDeserializer(HashCode.class, new JacksonJsonHashCodeDeserializer());
		jsonModule.addDeserializer(byte[].class, new JacksonJsonBytesDeserializer());
		jsonModule.addDeserializer(String.class, new JacksonJsonStringDeserializer());
		jsonModule.addDeserializer(SerializerDummy.class, new JacksonSerializerDummyDeserializer());
		jsonModule.addDeserializer(UInt256.class, new JacksonJsonObjectStringDeserializer<>(
				UInt256.class,
				JacksonCodecConstants.U20_STR_VALUE,
				UInt256::from
		));
		jsonModule.addDeserializer(UInt384.class, new JacksonJsonObjectStringDeserializer<>(
				UInt384.class,
				JacksonCodecConstants.U30_STR_VALUE,
				UInt384::from
		));
		jsonModule.addDeserializer(REAddr.class, new JacksonJsonObjectStringDeserializer<>(
				REAddr.class,
				JacksonCodecConstants.RRI_STR_VALUE,
				s -> REAddr.of(Hex.decode(s))
		));
		jsonModule.addDeserializer(AID.class, new JacksonJsonObjectStringDeserializer<>(
				AID.class,
				JacksonCodecConstants.AID_STR_VALUE,
				AID::from
		));
		jsonModule.addKeyDeserializer(AID.class, new KeyDeserializer() {
			@Override
			public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
				if (!key.startsWith(JacksonCodecConstants.AID_STR_VALUE)) {
					throw new InvalidFormatException(
						ctxt.getParser(),
						"Expecting prefix" + JacksonCodecConstants.AID_STR_VALUE,
						key,
						AID.class
					);
				}
				return AID.from(key.substring(JacksonCodecConstants.STR_VALUE_LEN));
			}
		});

		// Special modifier for Enum values to remove :str: leadin from front
		jsonModule.setDeserializerModifier(new BeanDeserializerModifier() {
			@Override
			@SuppressWarnings("rawtypes")
			public JsonDeserializer<Enum> modifyEnumDeserializer(
					DeserializationConfig config,
					final JavaType type,
					BeanDescription beanDesc,
					final JsonDeserializer<?> deserializer
			) {
				return new JsonDeserializer<>() {
					@Override
					@SuppressWarnings("unchecked")
					public Enum deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
						String name = jp.getValueAsString();
						if (!name.startsWith(JacksonCodecConstants.STR_STR_VALUE)) {
							throw new IllegalStateException(String.format(
								"Expected value starting with %s, found: %s",
								JacksonCodecConstants.STR_STR_VALUE,
								name)
							);
						}
						Class<? extends Enum> rawClass = (Class<Enum<?>>) type.getRawClass();
						return Enum.valueOf(rawClass, jp.getValueAsString().substring(JacksonCodecConstants.STR_VALUE_LEN));
					}
				};
			}
		});

		serializationModifier.ifPresent(jsonModule::setSerializerModifier);

		registerModule(jsonModule);
	}
}
