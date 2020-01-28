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

import java.util.Collection;

import org.radix.serialization2.SerializerConstants;
import org.radix.serialization2.SerializerIds;

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

/**
 * TypeResolverBuilder that outputs type information for all classes that
 * are part of the serializable class set.  This set consists of all classes
 * annotated with {@link org.radix.serialization2.SerializerId2} and their
 * superclasses.
 */
class DsonTypeResolverBuilder extends ObjectMapper.DefaultTypeResolverBuilder {
	private static final long serialVersionUID = 29L;

	private final SerializerIds idLookup;

	DsonTypeResolverBuilder(SerializerIds idLookup) {
		super(ObjectMapper.DefaultTyping.NON_FINAL);
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
	protected TypeIdResolver idResolver(MapperConfig<?> config, JavaType baseType, Collection<NamedType> subtypes,
			boolean forSer, boolean forDeser) {
		return new DsonTypeIdResolver(baseType, config.getTypeFactory(), idLookup);
	}
}
