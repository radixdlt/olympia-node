package org.radix.network2.transport;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;

/**
 * Class that implements {@link TransportMetadata} and provides
 * an functionality where the underlying values may change dynamically.
 * Values are provided by {@link Supplier} returning {@code String}.
 */
public final class DynamicTransportMetadata implements TransportMetadata {
	private static final Supplier<String> NULL_SUPPLIER = () -> null;

	private final ImmutableSortedMap<String, Supplier<String>> metadata;

	/**
	 * Returns a {@code DynamicTransportMetadata} containing all the mappings
	 * contained in the supplied map.
	 *
	 * @param metadata The {@link Map} supplying the properties for the metadata
	 * @return The metadata
	 */
	public static DynamicTransportMetadata from(Map<String, Supplier<String>> metadata) {
		return new DynamicTransportMetadata(ImmutableSortedMap.copyOf(metadata));
	}

	/**
	 * Returns a {@code DynamicTransportMetadata} containing all the mappings
	 * contained in the supplied immutable map.
	 *
	 * @param metadata The {@link ImmutableMap} supplying the properties for the metadata
	 * @return The metadata
	 */
	public static DynamicTransportMetadata from(ImmutableSortedMap<String, Supplier<String>> metadata) {
		return new DynamicTransportMetadata(metadata);
	}

	/**
	 * Returns a {@code DynamicTransportMetadata} containing the mapping
	 * from the specified key to the specified value supplier.
	 *
	 * @param k1 the specified key
	 * @param v1 the specified value supplier
	 * @return The metadata
	 */
	public static DynamicTransportMetadata of(String k1, Supplier<String> v1) {
		return new DynamicTransportMetadata(ImmutableSortedMap.of(k1, v1));
	}

	/**
	 * Returns a {@code DynamicTransportMetadata} containing the mapping
	 * from the specified keys to the specified value suppliers.
	 *
	 * @param k1 the first specified key
	 * @param v1 the first specified value supplier
	 * @param k2 the second specified key
	 * @param v2 the second specified value supplier
	 * @return The metadata
	 */
	public static DynamicTransportMetadata of(String k1, Supplier<String> v1, String k2, Supplier<String> v2) {
		return new DynamicTransportMetadata(ImmutableSortedMap.of(k1, v1, k2, v2));
	}

	@JsonCreator
	private DynamicTransportMetadata(ImmutableSortedMap<String, Supplier<String>> metadata) {
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
	// For serialiser -> serialise to static values
	private ImmutableMap<String, String> jsonValue() {
		return metadata.entrySet().stream()
			.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> e.getValue().get()));
	}

	@Override
	public String toString() {
		return jsonValue().toString();
	}
}
