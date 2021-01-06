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
		objectMapper.configure(MapperFeature.STRICT_PROPERTIES_ORDERING, sortProperties);
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
