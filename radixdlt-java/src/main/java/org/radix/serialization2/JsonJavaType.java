package org.radix.serialization2;

import java.util.Objects;

import com.fasterxml.jackson.databind.JavaType;

/**
 * Opaque wrapper for {@link JavaType} to allow distinguishing between
 * JSON and DSON mapper.
 */
public class JsonJavaType {
	private final JavaType type;

	JsonJavaType(JavaType type) {
		this.type = Objects.requireNonNull(type);
	}

	JavaType javaType() {
		return this.type;
	}
}
