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

package com.radixdlt.sanitytestsuite.scenario.jsonserialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public final class ArgumentsExtractor {

	private final Set<String> fieldsExtracted;
	private final JsonNode argumentsObject;

	ArgumentsExtractor(JsonNode argumentsObject) {
		this.fieldsExtracted = new HashSet<>();
		this.argumentsObject = argumentsObject;
	}

	<T> T extractArgument(String fieldNameInJsonObject, Function<JsonNode, T> mapper) {
		if (fieldsExtracted.contains(fieldNameInJsonObject)) {
			throw new RuntimeException("Field with name " + fieldNameInJsonObject + " already extracted.");
		}

		JsonNode fieldValueJSONObject = Objects.requireNonNull(argumentsObject.get(fieldNameInJsonObject));

		fieldsExtracted.add(fieldNameInJsonObject);

		return Objects.requireNonNull(mapper.apply(fieldValueJSONObject));
	}

	<T> T extractArgumentAsStringAndMapTo(String fieldNameInJsonObject, Function<String, T> mapper) {
		return Objects.requireNonNull(
			mapper.apply(extractArgument(fieldNameInJsonObject, JsonNode::asText))
		);

	}

	static <OldKey, OldValue, NewKey, NewValue> Map<NewKey, NewValue> mapMap(
		Map<OldKey, OldValue> oldMap,
		Function<OldKey, NewKey> mapKey,
		Function<OldValue, NewValue> mapValue
	) {
		return oldMap.entrySet().stream()
			.map(e -> Pair.of(
				mapKey.apply(e.getKey()),
				mapValue.apply(e.getValue())
				)
			)
			.collect(ImmutableMap.toImmutableMap(Pair::getFirst, Pair::getSecond));
	}

	<K, V> Map<K, V> extractMap(String named, Function<String, K> mapKey, Function<JsonNode, V> mapValue) {
		return mapMap(
			this.extractArgument(named,
				(
					jsonNode ->
						Stream.iterate(jsonNode.fields(), Iterator::hasNext, UnaryOperator.identity())
							.map(Iterator::next)
						.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)))
			),
			mapKey,
			mapValue
		);
	}

	<K, V> Map<K, V> extractMapWithValueAsStringAndMapTo(String named, Function<String, K> mapKey, Function<String, V> mapValue) {
		return mapMap(
			this.extractMap(named, Function.identity(), JsonNode::asText),
			mapKey,
			mapValue
		);
	}

	long extractLong(String named) {
		return extractArgumentAsStringAndMapTo(named, Long::parseLong);
	}

	UInt256 extractUInt256(String named) {
		return extractArgumentAsStringAndMapTo(named, UInt256::from);
	}

	RRI extractRRI(String named) {
		return extractArgumentAsStringAndMapTo(named, RRI::from);
	}

	RadixAddress extractRadixAddress(String named) {
		return extractArgumentAsStringAndMapTo(named, RadixAddress::from);
	}

	Map<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> extractTokenPermissions(String named) {
		return extractMapWithValueAsStringAndMapTo(
			named,
			k -> MutableSupplyTokenDefinitionParticle.TokenTransition.valueOf(k.toUpperCase()),
			v -> TokenPermission.valueOf(v.toUpperCase())
		);

	}

	boolean isFinished() {
		int numberOfFieldsExtracted = fieldsExtracted.size();
		int numberOfFieldsInOriginalObject = argumentsObject.size();

		if (numberOfFieldsExtracted > numberOfFieldsInOriginalObject) {
			throw new RuntimeException("Incorrect implementation, should never have extracted more fields than the object contained.");
		}

		return numberOfFieldsExtracted == numberOfFieldsInOriginalObject;
	}
}