/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radix.acceptance;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableMap;

/**
 * Class for maintaining a map of string names to string values,
 * where allowable names and default values can be specified.
 */
public class SpecificProperties {
	private final ImmutableMap<String, String> defaultValues;
	private final Map<String, String> propertyValues = new HashMap<>();

	SpecificProperties(String... propertyNamesAndValues) {
		ImmutableMap.Builder<String, String> defaults = ImmutableMap.builder();
		for (int i = 0; i < propertyNamesAndValues.length; i += 2) {
			defaults.put(propertyNamesAndValues[i], propertyNamesAndValues[i + 1]);
		}
		this.defaultValues = defaults.build();
		this.propertyValues.putAll(this.defaultValues);
	}

	/**
	 * Retrieves a value for a given property name.
	 * @param name The property name to retrieve the value for.
	 * @return The property value
	 * @throws IllegalArgumentException if the given property does not have a value
	 */
	public String get(String name) {
		Objects.requireNonNull(name);
		if (!this.propertyValues.containsKey(name)) {
			throw new IllegalArgumentException("No such property: " + name);
		}
		return this.propertyValues.get(name);
	}

	/**
	 * Associates a value with a given property name.
	 * @param name The property name
	 * @param value The property value
	 * @throws IllegalArgumentException if the specified property name is not known
	 */
	public void put(String name, String value) {
		Objects.requireNonNull(name);
		Objects.requireNonNull(value);
		if (!this.defaultValues.containsKey(name)) {
			throw new IllegalArgumentException("Invalid property name: " + name);
		}
		this.propertyValues.put(name, value);
	}

	/**
	 * Resets this property map to default values.
	 */
	public void clear() {
		this.propertyValues.clear();
		this.propertyValues.putAll(this.defaultValues);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.defaultValues, this.propertyValues);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof SpecificProperties) {
			SpecificProperties other = (SpecificProperties) obj;
			return Objects.equals(this.defaultValues, other.defaultValues)
				&& Objects.equals(this.propertyValues, other.propertyValues);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[defaults=%s, values=%s]",
				getClass().getSimpleName(), defaultValues, propertyValues);
	}

	/**
	 * Constructs a properties map of names to default values.
	 * @param propertyNamesAndValues A sequence of alternating property names and
	 * 		default values.  A default value of {@code null} may be used to indicate
	 * 		no default value.  There must therefore be an even number of elements
	 * 		in this sequence.
	 * @return The corresponding properties map.
	 */
	public static SpecificProperties of(String... propertyNamesAndValues) {
		if ((propertyNamesAndValues.length % 2) != 0) {
			throw new IllegalArgumentException("Must specify names and values");
		}
		return new SpecificProperties(propertyNamesAndValues);
	}
}
