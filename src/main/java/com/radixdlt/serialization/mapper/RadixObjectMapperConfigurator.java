package com.radixdlt.serialization.mapper;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.radixdlt.serialization.SerializerIds;

public class RadixObjectMapperConfigurator {

	private RadixObjectMapperConfigurator() {
		throw new IllegalStateException("Class instance creation is not allowed");
	}

	public static void configure(
			ObjectMapper objectMapper,
			SerializerIds idLookup,
			FilterProvider filterProvider,
			boolean sortProperties
	) {
		objectMapper.registerModule(new JsonOrgModule());
		objectMapper.registerModule(new GuavaModule());

		objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, sortProperties);
		objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, sortProperties);
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
				.withFieldVisibility(JsonAutoDetect.Visibility.NONE)
				.withGetterVisibility(JsonAutoDetect.Visibility.NONE)
				.withSetterVisibility(JsonAutoDetect.Visibility.NONE)
				.withCreatorVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY));
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
		objectMapper.setFilterProvider(filterProvider);
		objectMapper.setAnnotationIntrospector(new DsonFilteringIntrospector());
		objectMapper.setDefaultTyping(new DsonTypeResolverBuilder(idLookup));
	}
}
