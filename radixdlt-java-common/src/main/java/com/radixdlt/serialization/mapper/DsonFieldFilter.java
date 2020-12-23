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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Map;

/**
 * A field filter for DSON output modes.
 */
public class DsonFieldFilter extends SimpleBeanPropertyFilter {
	private final ImmutableMap<Class<?>, ImmutableSet<String>> includedItems;

	/**
	 * Create a {@link FilterProvider} with a {@link DsonFieldFilter}
	 * @param includedFields Set of {@code Class<?>} and field names to include for this filter.
	 * @return A freshly created {@link FilterProvider} with the specified DSON filter included.
	 */
	public static FilterProvider filterProviderFor(ImmutableMap<Class<?>, ImmutableSet<String>> includedFields) {
		return new SimpleFilterProvider().addFilter(MapperConstants.DSON_FILTER_NAME, new DsonFieldFilter(includedFields));
	}

	DsonFieldFilter(ImmutableMap<Class<?>, ImmutableSet<String>> includedItems) {
		// No need to make copy of immutable set.
		this.includedItems = includedItems;
	}

	@Override
	public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer)
			throws Exception {
        Object parent = jgen.getOutputContext().getCurrentValue();
		if (shouldInclude(parent.getClass(), writer.getName())) {
			writer.serializeAsField(pojo, jgen, provider);
		} else if (!jgen.canOmitFields()) { // since 2.3
			writer.serializeAsOmittedField(pojo, jgen, provider);
		} else {
			// Field can just be omitted in this case, which means there is nothing to do here
		}
	}

	@Override
	protected boolean include(BeanPropertyWriter writer) {
		return true;
	}

	@Override
	protected boolean include(PropertyWriter writer) {
		return true;
	}

	private boolean shouldInclude(Class<?> cls, String property) {
		// Maps and Collection sub-classes always have contents serialised
		if (Map.class.isAssignableFrom(cls) || Collection.class.isAssignableFrom(cls)) {
			return true;
		}
		ImmutableSet<String> fieldsForClass = this.includedItems.get(cls);
		return fieldsForClass != null && fieldsForClass.contains(property);
	}
}
