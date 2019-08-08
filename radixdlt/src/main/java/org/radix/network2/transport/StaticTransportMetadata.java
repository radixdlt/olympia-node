package org.radix.network2.transport;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;

public final class StaticTransportMetadata implements TransportMetadata {
	@JsonValue
	private final ImmutableMap<String, String> metadata;

	public static StaticTransportMetadata from(Map<String, String> metadata) {
		return new StaticTransportMetadata(ImmutableMap.copyOf(metadata));
	}

	public static StaticTransportMetadata from(ImmutableMap<String, String> metadata) {
		return new StaticTransportMetadata(metadata);
	}

	public static StaticTransportMetadata of(String k1, String v1) {
		return new StaticTransportMetadata(ImmutableMap.of(k1, v1));
	}

	public static StaticTransportMetadata of(String k1, String v1, String k2, String v2) {
		return new StaticTransportMetadata(ImmutableMap.of(k1, v1, k2, v2));
	}

	@JsonCreator
	private StaticTransportMetadata(ImmutableMap<String, String> metadata) {
		this.metadata = Objects.requireNonNull(metadata);
	}

	@Override
	public String get(String property) {
		return metadata.get(property);
	}

	@Override
	public int hashCode() {
		return metadata.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof StaticTransportMetadata) {
			StaticTransportMetadata other = (StaticTransportMetadata) obj;
			return Objects.equals(this.metadata, other.metadata);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), metadata);
	}
}
