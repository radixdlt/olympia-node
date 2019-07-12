package com.radixdlt.serialization.mapper;

import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

/**
 * An annotation introspector that defaults to using the DSON filter
 * if no other filter is specified.
 */
class DsonFilteringIntrospector extends JacksonAnnotationIntrospector {
	private static final long serialVersionUID = 29L;

	@Override
	public Object findFilterId(Annotated a) {
		Object id = super.findFilterId(a);
		if (id == null) {
			return MapperConstants.DSON_FILTER_NAME;
		}
		return id;
	}
}
