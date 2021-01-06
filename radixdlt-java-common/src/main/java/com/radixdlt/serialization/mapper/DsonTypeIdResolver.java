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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.radixdlt.serialization.SerializerIds;
import java.io.IOException;

/**
 * {@link com.fasterxml.jackson.databind.jsontype.TypeIdResolver} implementation
 * that converts between fully-qualified Java class names and (JSON) Strings.
 */
public class DsonTypeIdResolver extends TypeIdResolverBase {
	private final SerializerIds idLookup;

	public DsonTypeIdResolver(JavaType baseType, TypeFactory typeFactory, SerializerIds idLookup) {
		super(baseType, typeFactory);
		this.idLookup = idLookup;
	}

	@Override
	public JsonTypeInfo.Id getMechanism() {
		return JsonTypeInfo.Id.CUSTOM;
	}

	@Override
	public String idFromValue(Object value) {
		return this.idLookup.getIdForClass(value.getClass());
	}

	@Override
	public String idFromValueAndType(Object value, Class<?> type) {
		return this.idLookup.getIdForClass(type);
	}

	@Override
	public JavaType typeFromId(DatabindContext context, String id) throws IOException {
		return _typeFactory.constructType(idLookup.getClassForId(id));
	}

	@Override
	public String getDescForKnownTypeIds() {
		return "DSON serializer id used as type id";
	}
}
