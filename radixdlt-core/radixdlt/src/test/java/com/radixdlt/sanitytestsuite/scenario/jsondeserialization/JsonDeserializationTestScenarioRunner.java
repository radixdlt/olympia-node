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

package com.radixdlt.sanitytestsuite.scenario.jsondeserialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.sanitytestsuite.utility.ArgumentsExtractor;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Pair;
import java.util.Map;
import java.util.function.BiConsumer;

import static java.util.Optional.ofNullable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class JsonDeserializationTestScenarioRunner extends SanityTestScenarioRunner<JsonDeserializationTestVector> {
	private final Serialization serialization = DefaultSerialization.getInstance();
	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public String testScenarioIdentifier() {
		return "json_deserialization_radix_models";
	}

	@Override
	public Class<JsonDeserializationTestVector> testVectorType() {
		return JsonDeserializationTestVector.class;
	}

	private static void assertEqualModelTTP(final Map<String, Object> arguments, final Object expectedObject) {
		var expected = (TransferrableTokensParticle) expectedObject;
		var argsExtractor = ArgumentsExtractor.from(arguments);

		ImmutableList.of(
			Pair.of(
				expected.getAddress(),
				argsExtractor.asRadixAddress("address")
			),
			Pair.of(
				expected.getAmount(),
				argsExtractor.asUInt256("amount")
			),
			Pair.of(
				expected.getGranularity(),
				argsExtractor.asUInt256("granularity")
			),
			Pair.of(
				expected.getTokDefRef(),
				argsExtractor.asRRI("tokenDefinitionReference")
			),
			Pair.of(
				expected.getNonce(),
				argsExtractor.asLong("nonce")
			),
			Pair.of(
				expected.getTokenPermissions(),
				argsExtractor.extractTokenPermissions("tokenPermissions")
			)
		).forEach(
			p -> assertEquals(p.getFirst(), p.getSecond())
		);

		assertTrue(argsExtractor.isFinished());
	}

	private static final Map<String, BiConsumer<Map<String, Object>, Object>> assertEqualsMap = ImmutableMap.of(
		"radix.particles.transferrable_tokens", JsonDeserializationTestScenarioRunner::assertEqualModelTTP
	);

	@Override
	public void doRunTestVector(JsonDeserializationTestVector testVector) throws AssertionError {

		var assertEqualModel = ofNullable(
			assertEqualsMap.get(testVector.input.typeSerialization)
		).orElseThrow(() -> new IllegalStateException("Can't find assertEqualModel method"));

		var deserializedModel = ofNullable(testVector.input.json)
			.map(jsonMap -> {
				try {
					return mapper.writeValueAsString(jsonMap);
				} catch (JsonProcessingException e) {
					throw new IllegalStateException("Failed to create JSON string from JSON object.");
				}
			})
			.map(jsonString -> {
				try {
					return serialization.fromJson(jsonString, serialization.getClassForId(testVector.input.typeSerialization));
				} catch (DeserializeException e) {
					throw new AssertionError("Failed to deserialize model from JSON string.", e);
				}
			}).orElseThrow(() -> new IllegalStateException("Can't deserialize model"));

		assertEqualModel.accept(
			testVector.expected.arguments,
			deserializedModel
		);

	}
}

