package com.radixdlt.serialization;

import java.util.Objects;

import com.fasterxml.jackson.databind.JavaType;

/**
 * Opaque wrapper for {@link JavaType} to allow distinguishing between
 * JSON and DSON mapper.
 */
public class DsonJavaType {
	private final JavaType type;

	DsonJavaType(JavaType type) {
		this.type = Objects.requireNonNull(type);
	}

	JavaType javaType() {
		return this.type;
	}
}
