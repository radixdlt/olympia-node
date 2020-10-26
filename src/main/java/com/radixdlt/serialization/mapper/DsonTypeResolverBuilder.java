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

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerIds;
import java.util.Collection;

/**
 * TypeResolverBuilder that outputs type information for all classes that
 * are part of the serializable class set.  This set consists of all classes
 * annotated with {@link com.radixdlt.serialization.SerializerId2} and their
 * superclasses.
 */
class DsonTypeResolverBuilder extends ObjectMapper.DefaultTypeResolverBuilder {
	private static final long serialVersionUID = 29L;

	private final SerializerIds idLookup;

	DsonTypeResolverBuilder(SerializerIds idLookup) {
		super(ObjectMapper.DefaultTyping.NON_FINAL, LaissezFaireSubTypeValidator.instance);
		init(Id.CUSTOM, null).inclusion(As.EXISTING_PROPERTY).typeProperty(SerializerConstants.SERIALIZER_NAME);
		this.idLookup = idLookup;
	}

	@Override
	public TypeSerializer buildTypeSerializer(SerializationConfig config, JavaType baseType, Collection<NamedType> subtypes) {
		// Serialization handled already
		return null;
	}

	@Override
	public TypeDeserializer buildTypeDeserializer(DeserializationConfig config, JavaType baseType, Collection<NamedType> subtypes) {
		return super.buildTypeDeserializer(config, baseType, subtypes);
	}

	@Override
	public boolean useForType(JavaType t) {
		return idLookup.isSerializableSuper(t.getRawClass());
	}

	@Override
	protected TypeIdResolver idResolver(
			MapperConfig<?> config, JavaType baseType,
			PolymorphicTypeValidator subtypeValidator, Collection<NamedType> subtypes,
			boolean forSer, boolean forDeser
	) {
		return new DsonTypeIdResolver(baseType, config.getTypeFactory(), idLookup);
	}
}
