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

import org.radix.serialization2.SerializerIds;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;

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

	public void registerSubtype(Class<?> type, String name) {
		// not used
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
