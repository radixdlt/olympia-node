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
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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
		// FIXME: Special cases ideally handled better
		if (Map.class.isAssignableFrom(cls) || Collection.class.isAssignableFrom(cls)) {
			return true;
		}
		ImmutableSet<String> fieldsForClass = this.includedItems.get(cls);
		return fieldsForClass == null ? false : fieldsForClass.contains(property);
	}
}
