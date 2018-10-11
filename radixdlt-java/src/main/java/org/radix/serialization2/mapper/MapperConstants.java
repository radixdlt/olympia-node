package org.radix.serialization2.mapper;

import org.radix.common.tuples.Pair;
import org.radix.serialization2.SerializerDummy;

final class MapperConstants {
	private MapperConstants() {
		throw new IllegalStateException("Can't construct");
	}

	static final String SERIALIZER_FIELD_NAME = "serializer";
	static final Pair<Class<?>, String> SERIALIZER_FIELD = Pair.of(SerializerDummy.class, SERIALIZER_FIELD_NAME);

	static final String DSON_FILTER_NAME = "DSON";
}
