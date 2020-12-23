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

package com.radixdlt.network.transport;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableSortedMap;

/**
 * Class that implements {@link TransportMetadata} and provides
 * functionality where the underlying values are static.
 */
public final class StaticTransportMetadata implements TransportMetadata {
	@JsonValue
	private final ImmutableSortedMap<String, String> metadata;

	/**
	 * Returns an empty {@link StaticTransportMetadata}.
	 * @return an empty metadata
	 */
	public static StaticTransportMetadata empty() {
		return new StaticTransportMetadata(ImmutableSortedMap.of());
	}

	/**
	 * Returns a {@code StaticTransportMetadata} containing all the mappings
	 * contained in the supplied map.
	 *
	 * @param metadata The {@link Map} supplying the properties for the metadata
	 * @return The metadata
	 */
	public static StaticTransportMetadata from(Map<String, String> metadata) {
		return new StaticTransportMetadata(ImmutableSortedMap.copyOf(metadata));
	}

	/**
	 * Returns a {@code StaticTransportMetadata} containing all the mappings
	 * contained in the supplied map.
	 *
	 * @param metadata The {@link ImmutableMap} supplying the properties for the metadata
	 * @return The metadata
	 */
	public static StaticTransportMetadata from(ImmutableSortedMap<String, String> metadata) {
		return new StaticTransportMetadata(metadata);
	}

	/**
	 * Returns a {@code StaticTransportMetadata} containing the mapping
	 * from the specified key to the specified values.
	 *
	 * @param k1 the specified key
	 * @param v1 the specified value
	 * @return The metadata
	 */
	public static StaticTransportMetadata of(String k1, String v1) {
		return new StaticTransportMetadata(ImmutableSortedMap.of(k1, v1));
	}

	/**
	 * Returns a {@code StaticTransportMetadata} containing the mapping
	 * from the specified keys to the specified values.
	 *
	 * @param k1 the first specified key
	 * @param v1 the first specified value
	 * @param k2 the second specified key
	 * @param v2 the second specified value
	 * @return The metadata
	 */
	public static StaticTransportMetadata of(String k1, String v1, String k2, String v2) {
		return new StaticTransportMetadata(ImmutableSortedMap.of(k1, v1, k2, v2));
	}

	@JsonCreator
	private StaticTransportMetadata(ImmutableSortedMap<String, String> metadata) {
		this.metadata = Objects.requireNonNull(metadata);
	}

	@Override
	public String get(String property) {
		return metadata.get(property);
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
		if (obj instanceof StaticTransportMetadata) {
			StaticTransportMetadata other = (StaticTransportMetadata) obj;
			return Objects.equals(this.metadata, other.metadata);
		}
		return false;
	}

	@Override
	public String toString() {
		return this.metadata.toString();
	}
}
