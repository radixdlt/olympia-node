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

import org.radix.common.ID.AID;
import org.radix.common.ID.EUID;
import org.radix.crypto.Hash;
import org.radix.serialization2.SerializerDummy;
import org.radix.serialization2.SerializerIds;
import org.radix.utils.UInt256;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.RRI;

/**
 * A Jackson {@link ObjectMapper} that will serialize and deserialize
 * to the JSON in the format that Radix requires.
 */
public class JacksonJsonMapper extends ObjectMapper {
	private static final long serialVersionUID = 4917479892309630214L;

	private JacksonJsonMapper() {
		super(createJsonFactory());
	}

	private static JsonFactory createJsonFactory() {
		return new JsonFactory();
	}

	/**
	 * Create an {@link ObjectMapper} that will serialize to/from the JSON
	 * format that radix requires.
	 *
	 * @param idLookup A {@link SerializerIds} used to perform serializer
	 * 		ID lookup
	 * @param filterProvider A {@link FilterProvider} to use for filtering
	 * 		serialized fields
	 * @param sortProperties {@code true} if JSON output properties should be
	 * 		sorted in lexicographical order
	 * @return A freshly created {@link JacksonJsonMapper}
	 */
	public static JacksonJsonMapper create(SerializerIds idLookup, FilterProvider filterProvider, boolean sortProperties) {
		SimpleModule jsonModule = new SimpleModule();
		jsonModule.addSerializer(EUID.class, new JacksonJsonEUIDSerializer());
		jsonModule.addSerializer(Hash.class, new JacksonJsonHashSerializer());
		jsonModule.addSerializer(byte[].class, new JacksonJsonBytesSerializer());
		jsonModule.addSerializer(String.class, new JacksonJsonStringSerializer());
		jsonModule.addSerializer(SerializerDummy.class, new JacksonSerializerDummySerializer(idLookup));
		jsonModule.addSerializer(RadixAddress.class, new JacksonJsonObjectStringSerializer<>(
			JacksonCodecConstants.ADDR_STR_VALUE, RadixAddress::toString)
		);
		jsonModule.addSerializer(UInt256.class, new JacksonJsonObjectStringSerializer<>(
			UInt256.class,
			JacksonCodecConstants.U20_STR_VALUE,
			UInt256::toString
		));
		jsonModule.addSerializer(RRI.class, new JacksonJsonObjectStringSerializer<>(
			RRI.class,
			JacksonCodecConstants.RRI_STR_VALUE,
			RRI::toString
		));
		jsonModule.addSerializer(AID.class, new JacksonJsonObjectStringSerializer<>(
			AID.class,
			JacksonCodecConstants.AID_STR_VALUE,
			AID::toString
		));

		jsonModule.addDeserializer(EUID.class, new JacksonJsonEUIDDeserializer());
		jsonModule.addDeserializer(Hash.class, new JacksonJsonHashDeserializer());
		jsonModule.addDeserializer(byte[].class, new JacksonJsonBytesDeserializer());
		jsonModule.addDeserializer(String.class, new JacksonJsonStringDeserializer());
		jsonModule.addDeserializer(SerializerDummy.class, new JacksonSerializerDummyDeserializer());
		jsonModule.addDeserializer(RadixAddress.class, new JacksonJsonObjectStringDeserializer<>(
			RadixAddress.class, JacksonCodecConstants.ADDR_STR_VALUE, RadixAddress::from)
		);
		jsonModule.addDeserializer(UInt256.class, new JacksonJsonObjectStringDeserializer<>(
			UInt256.class,
			JacksonCodecConstants.U20_STR_VALUE,
			UInt256::from
		));
		jsonModule.addDeserializer(RRI.class, new JacksonJsonObjectStringDeserializer<>(
			RRI.class,
			JacksonCodecConstants.RRI_STR_VALUE,
			RRI::fromString
		));
		jsonModule.addDeserializer(AID.class, new JacksonJsonObjectStringDeserializer<>(
			AID.class,
			JacksonCodecConstants.AID_STR_VALUE,
			AID::from
		));

		// Special modifier for Enum values to remove :str: leadin from front
		jsonModule.setDeserializerModifier(new BeanDeserializerModifier() {
			@Override
			@SuppressWarnings("rawtypes")
			public JsonDeserializer<Enum> modifyEnumDeserializer(DeserializationConfig config, final JavaType type,
					BeanDescription beanDesc, final JsonDeserializer<?> deserializer) {
				return new JsonDeserializer<Enum>() {
					@Override
					@SuppressWarnings("unchecked")
					public Enum deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
						String name = jp.getValueAsString();
						if (!name.startsWith(JacksonCodecConstants.STR_STR_VALUE)) {
							throw new IllegalStateException(String.format("Expected value starting with %s, found: %s",
									JacksonCodecConstants.STR_STR_VALUE, name));
						}
						Class<? extends Enum> rawClass = (Class<Enum<?>>) type.getRawClass();
						return Enum.valueOf(rawClass, jp.getValueAsString().substring(JacksonCodecConstants.STR_VALUE_LEN));
					}
				};
			}
		});

		JacksonJsonMapper mapper = new JacksonJsonMapper();
		mapper.registerModule(jsonModule);
	    mapper.registerModule(new JsonOrgModule());
	    mapper.registerModule(new GuavaModule());

		mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, sortProperties);
		mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, sortProperties);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
				.withFieldVisibility(JsonAutoDetect.Visibility.NONE)
				.withGetterVisibility(JsonAutoDetect.Visibility.NONE)
				.withSetterVisibility(JsonAutoDetect.Visibility.NONE)
				.withCreatorVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY));
		mapper.setSerializationInclusion(Include.NON_EMPTY);
		mapper.setFilterProvider(filterProvider);
		mapper.setAnnotationIntrospector(new DsonFilteringIntrospector());
	    mapper.setDefaultTyping(new DsonTypeResolverBuilder(idLookup));
		return mapper;
	}

	/**
	 * Create an {@link ObjectMapper} that will serialize to/from the JSON
	 * format that radix requires.
	 *
	 * @param idLookup A {@link SerializerIds} used to perform serializer
	 * 		ID lookup
	 * @param filterProvider A {@link FilterProvider} to use for filtering
	 * 		serialized fields
	 * @return A freshly created {@link JacksonJsonMapper} that does not sort properties
	 * @see #create(SerializerIds, FilterProvider, boolean)
	 */
	public static JacksonJsonMapper create(SerializerIds idLookup, FilterProvider filterProvider) {
		return create(idLookup, filterProvider, false);
	}
}
