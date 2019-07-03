package com.radixdlt.serialization.mapper;

import java.io.IOException;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.RRI;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerIds;
import com.radixdlt.common.AID;
import org.radix.time.Timestamps;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

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
		jsonModule.addSerializer(EUID.class, new JacksonJsonObjectStringSerializer<>(
				EUID.class,
				JacksonCodecConstants.EUID_STR_VALUE,
				EUID::toString
		));
		jsonModule.addSerializer(Hash.class, new JacksonJsonHashSerializer());
		jsonModule.addSerializer(Timestamps.class, new JacksonTimestampsSerializer());
		jsonModule.addSerializer(byte[].class, new JacksonJsonBytesSerializer());
		jsonModule.addSerializer(String.class, new JacksonJsonStringSerializer());
		jsonModule.addSerializer(SerializerDummy.class, new JacksonSerializerDummySerializer(idLookup));
		jsonModule.addSerializer(RadixAddress.class, new JacksonJsonObjectStringSerializer<>(
				RadixAddress.class,
				JacksonCodecConstants.ADDR_STR_VALUE,
				RadixAddress::toString
		));
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

		jsonModule.addDeserializer(EUID.class, new JacksonJsonObjectStringDeserializer<>(
			EUID.class,
			JacksonCodecConstants.EUID_STR_VALUE,
			EUID::new
		));
		jsonModule.addDeserializer(Hash.class, new JacksonJsonHashDeserializer());
		jsonModule.addDeserializer(Timestamps.class, new JacksonTimestampsDeserializer());
		jsonModule.addDeserializer(byte[].class, new JacksonJsonBytesDeserializer());
		jsonModule.addDeserializer(String.class, new JacksonJsonStringDeserializer());
		jsonModule.addDeserializer(SerializerDummy.class, new JacksonSerializerDummyDeserializer());
		jsonModule.addDeserializer(RadixAddress.class, new JacksonJsonObjectStringDeserializer<>(
				RadixAddress.class,
				JacksonCodecConstants.ADDR_STR_VALUE,
				RadixAddress::from
		));
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
		jsonModule.addDeserializer(RRI.class, new JacksonJsonObjectStringDeserializer<>(
				RRI.class,
				JacksonCodecConstants.RRI_STR_VALUE,
				RRI::from
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
			public JsonDeserializer<Enum> modifyEnumDeserializer(DeserializationConfig config, final JavaType type, BeanDescription beanDesc, final JsonDeserializer<?> deserializer) {
				return new JsonDeserializer<Enum>() {
					@Override
					@SuppressWarnings("unchecked")
					public Enum deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
						String name = jp.getValueAsString();
						if (!name.startsWith(JacksonCodecConstants.STR_STR_VALUE)) {
							throw new IllegalStateException(String.format("Expected value starting with %s, found: %s", JacksonCodecConstants.STR_STR_VALUE, name));
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
