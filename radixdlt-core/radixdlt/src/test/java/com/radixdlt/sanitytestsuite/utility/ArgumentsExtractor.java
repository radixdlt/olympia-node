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

package com.radixdlt.sanitytestsuite.utility;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public final class ArgumentsExtractor {
	private final Set<String> fieldsExtracted = new HashSet<>();
	private final Map<String, Object> arguments;

	private ArgumentsExtractor(final Map<String, Object> arguments) {
		this.arguments = arguments;
	}

	public static ArgumentsExtractor from(final Map<String, Object> arguments) {
		return new ArgumentsExtractor(arguments);
	}

	<T> T extract(final String fieldName, final Function<Object, T> mapper) {
		if (fieldsExtracted.contains(fieldName)) {
			throw new IllegalArgumentException("Attempt to retrieve same field more than once. Field name " + fieldName);
		}
		fieldsExtracted.add(fieldName);

		return ofNullable(arguments.get(fieldName))
			.map(mapper)
			.orElseThrow(() -> new IllegalArgumentException("Field with name " + fieldName + " is absent"));
	}

	static <OldKey, OldValue, NewKey, NewValue> Map<NewKey, NewValue> mapMap(
		Map<OldKey, OldValue> oldMap,
		Function<OldKey, NewKey> mapKey,
		Function<OldValue, NewValue> mapValue
	) {
		return oldMap.entrySet()
			.stream()
			.map(e -> Pair.of(mapKey.apply(e.getKey()), mapValue.apply(e.getValue())))
			.collect(ImmutableMap.toImmutableMap(Pair::getFirst, Pair::getSecond));
	}

	@SuppressWarnings("unchecked")
	<K, V> Map<K, V> asMap(String named, Function<String, K> mapKey, Function<Object, V> mapValue) {
		var map = this.extract(named, object -> (Map<String, Object>) object);

		return mapMap(map, mapKey, mapValue);
	}

	<K, V> Map<K, V> extractAsMapAndConvert(String named, Function<String, K> mapKey, Function<String, V> mapValue) {
		return mapMap(
			asMap(named, Function.identity(), value -> (String) value),
			mapKey,
			mapValue
		);
	}

	<T> T extractAndMap(String named, Function<String, T> mapper) {
		return extract(named, value -> mapper.apply((String) value));
	}


	public String asString(String named) {
		return extractAndMap(named, Function.identity());
	}

	public long asLong(String named) {
		return extractAndMap(named, Long::parseLong);
	}

	public UInt256 asUInt256(String named) {
		return extractAndMap(named, UInt256::from);
	}

	public RRI asRRI(String named) {
		return extractAndMap(named, RRI::from);
	}

	public RadixAddress asRadixAddress(String named) {
		return extractAndMap(named, RadixAddress::from);
	}

	public Map<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> extractTokenPermissions(String named) {
		return extractAsMapAndConvert(
			named,
			k -> MutableSupplyTokenDefinitionParticle.TokenTransition.valueOf(k.toUpperCase()),
			v -> TokenPermission.valueOf(v.toUpperCase())
		);
	}

	public Set<RadixAddress> asAddressSet(String named) {
		return extract(
				named,
				object -> (List<String>) object).stream().map(RadixAddress::from).collect(Collectors.toSet()
		);
	}

	public boolean isFinished() {
		int numberOfFieldsExtracted = fieldsExtracted.size();
		int numberOfFieldsInOriginalObject = arguments.size();

		if (numberOfFieldsExtracted > numberOfFieldsInOriginalObject) {
			throw new RuntimeException("Incorrect implementation, should never have extracted more fields than the object contained.");
		}

		return numberOfFieldsExtracted == numberOfFieldsInOriginalObject;
	}
}