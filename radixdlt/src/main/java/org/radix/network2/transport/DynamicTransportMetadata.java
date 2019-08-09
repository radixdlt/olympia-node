package org.radix.network2.transport;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;

public final class DynamicTransportMetadata implements TransportMetadata {
	private static final Supplier<String> NULL_SUPPLIER = () -> null;

	private final ImmutableMap<String, Supplier<String>> metadata;

	public static DynamicTransportMetadata from(Map<String, Supplier<String>> metadata) {
		return new DynamicTransportMetadata(ImmutableMap.copyOf(metadata));
	}

	public static DynamicTransportMetadata from(ImmutableMap<String, Supplier<String>> metadata) {
		return new DynamicTransportMetadata(metadata);
	}

	public static DynamicTransportMetadata of(String k1, Supplier<String> v1) {
		return new DynamicTransportMetadata(ImmutableMap.of(k1, v1));
	}

	public static DynamicTransportMetadata of(String k1, Supplier<String> v1, String k2, Supplier<String> v2) {
		return new DynamicTransportMetadata(ImmutableMap.of(k1, v1, k2, v2));
	}

	@JsonCreator
	private DynamicTransportMetadata(ImmutableMap<String, Supplier<String>> metadata) {
		this.metadata = Objects.requireNonNull(metadata);
	}

	@Override
	public String get(String property) {
		return metadata.getOrDefault(property, NULL_SUPPLIER).get();
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(metadata);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof DynamicTransportMetadata) {
			DynamicTransportMetadata other = (DynamicTransportMetadata) obj;
			return Objects.equals(this.metadata, other.metadata);
		}
		return false;
	}

	@JsonValue
	private ImmutableMap<String, String> jsonValue() {
		return metadata.entrySet().stream()
			.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> e.getValue().get()));
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), jsonValue());
	}
}
