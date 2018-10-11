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
		Long id = this.idLookup.getIdForClass(value.getClass());
		return String.valueOf(id);
	}

	@Override
	public String idFromValueAndType(Object value, Class<?> type) {
		Long id = this.idLookup.getIdForClass(type);
		return String.valueOf(id);
	}

	@Override
	public JavaType typeFromId(DatabindContext context, String id) throws IOException {
		long lid = Long.parseLong(id);
		return _typeFactory.constructType(idLookup.getClassForId(lid));
	}

	@Override
	public String getDescForKnownTypeIds() {
		return "DSON serializer id used as type id";
	}
}
