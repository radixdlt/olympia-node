package com.radix.acceptance.create_single_issuance_token_class;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

class SpecificProperties {
	private final ImmutableSet<String> propertyNames;
	private final ImmutableMap<String, String> defaultValues;
	private final Map<String, String> propertyValues = new HashMap<>();

	SpecificProperties(String... propertyNamesAndValues) {
		ImmutableSet.Builder<String> names = ImmutableSet.builder();
		ImmutableMap.Builder<String, String> defaults = ImmutableMap.builder();
		for (int i = 0; i < propertyNamesAndValues.length; i += 2) {
			String name = propertyNamesAndValues[i];
			String value = propertyNamesAndValues[i + 1];
			names.add(name);
			defaults.put(name, value);
		}
		this.propertyNames = names.build();
		this.defaultValues = defaults.build();
		this.propertyValues.putAll(this.defaultValues);
	}

	String get(String name) {
		Objects.requireNonNull(name);
		if (!this.propertyValues.containsKey(name)) {
			throw new IllegalArgumentException("No such property: " + name);
		}
		return this.propertyValues.get(name);
	}

	void put(String name, String value) {
		Objects.requireNonNull(name);
		Objects.requireNonNull(value);
		if (!this.propertyNames.contains(name)) {
			throw new IllegalArgumentException("Invalid property name: " + name);
		}
		this.propertyValues.put(name, value);
	}

	void clear() {
		this.propertyValues.clear();
		this.propertyValues.putAll(this.defaultValues);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.propertyNames, this.defaultValues, this.propertyValues);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof SpecificProperties) {
			SpecificProperties other = (SpecificProperties) obj;
			return Objects.equals(this.propertyNames, other.propertyNames) &&
					Objects.equals(this.defaultValues, other.defaultValues) &&
					Objects.equals(this.propertyValues, other.propertyValues);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[names=%s, defaults=%s, values=%s]",
				getClass().getSimpleName(), propertyNames, defaultValues, propertyValues);
	}

	static SpecificProperties of(String... propertyNamesAndValues) {
		if ((propertyNamesAndValues.length % 2) != 0) {
			throw new IllegalArgumentException("Must specify names and values");
		}
		return new SpecificProperties(propertyNamesAndValues);
	}
}
