package com.radix.acceptance.create_single_issuance_token_class;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableSet;

class SpecificProperties {
	private final ImmutableSet<String> propertyNames;
	private final Map<String, String> propertyValues = new HashMap<>();

	SpecificProperties(String... propertyNames) {
		this.propertyNames = ImmutableSet.copyOf(propertyNames);
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
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.propertyNames, this.propertyValues);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof SpecificProperties) {
			SpecificProperties other = (SpecificProperties) obj;
			return Objects.equals(this.propertyNames, other.propertyNames) &&
					Objects.equals(this.propertyValues, other.propertyValues);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[names=%s, values=%s]", getClass().getSimpleName(), propertyNames, propertyValues);
	}

	static SpecificProperties of(String... propertyNames) {
		return new SpecificProperties(propertyNames);
	}
}
