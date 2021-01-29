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

package com.radixdlt.sanitytestsuite.scenario.deserialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.sanitytestsuite.utility.ArgumentsExtractor;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import org.bouncycastle.util.encoders.Hex;

import java.util.Map;
import java.util.function.BiConsumer;

import static java.util.Optional.ofNullable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DeserializationTestScenarioRunner extends SanityTestScenarioRunner<DeserializationTestVector> {

	private final static Serialization serialization = DefaultSerialization.getInstance();
	private final ObjectMapper mapper = new ObjectMapper();

	private static ImmutableMap<String, BiConsumer<Map<String, Object>, Object>> assertEqualsMap;

	public DeserializationTestScenarioRunner() {
		Map<String, BiConsumer<Map<String, Object>, Object>> mutableMap = Maps.newHashMap();
		mutableMap.put("radix.particles.transferrable_tokens", DeserializationTestScenarioRunner::assertTransferableTokensParticle);
		mutableMap.put("radix.particles.fixed_supply_token_definition", DeserializationTestScenarioRunner::assertFixedSupplyTokenDefinitionParticle);
		mutableMap.put("radix.particles.mutable_supply_token_definition", DeserializationTestScenarioRunner::assertMutableSupplyTokenDefinitionParticle);
		mutableMap.put("radix.particles.staked_tokens", DeserializationTestScenarioRunner::assertStakedTokensParticle);
		mutableMap.put("radix.particles.unallocated_tokens", DeserializationTestScenarioRunner::assertUnallocatedTokensParticle);
		mutableMap.put("radix.particles.rri", DeserializationTestScenarioRunner::assertRRIParticle);
		mutableMap.put("radix.particles.registered_validator", DeserializationTestScenarioRunner::assertRegisteredValidatorParticle);
		mutableMap.put("radix.particles.unregistered_validator", DeserializationTestScenarioRunner::assertUnregisteredValidatorParticle);
		mutableMap.put("radix.particles.system_tokens", DeserializationTestScenarioRunner::assertSystemParticle);
		assertEqualsMap = ImmutableMap.copyOf(mutableMap);
	}

	@Override
	public String testScenarioIdentifier() {
		return "deserialization_radix_models";
	}

	@Override
	public Class<DeserializationTestVector> testVectorType() {
		return DeserializationTestVector.class;
	}

	@Override
	public void doRunTestVector(DeserializationTestVector testVector) throws AssertionError {
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

		assertEqualDson(testVector, deserializedModel, Particle.class);
	}

	private static void assertEqualDson(DeserializationTestVector testVector, Object deserializedModel, Class<? extends Particle> classToDeserializeAs) {
		try {
			byte[] dsonAllOutput = Hex.decode(testVector.input.dson.get("allOutputHex").toString());
			Particle particleDeserializedFromAllOutput = serialization.fromDson(dsonAllOutput, classToDeserializeAs);
			assertEquals(deserializedModel, particleDeserializedFromAllOutput);
			byte[] dsonHashOutput = Hex.decode(testVector.input.dson.get("hashOutputHex").toString());
			Particle particleDeserializedFromHashOutput = serialization.fromDson(dsonHashOutput, classToDeserializeAs);
			assertEquals(deserializedModel, particleDeserializedFromHashOutput);
		} catch(DeserializeException e) {
			throw new RuntimeException(e);
		}
	}

	private static void assertTransferableTokensParticle(final Map<String, Object> arguments, final Object expectedObject) {
		TransferrableTokensParticle expected = (TransferrableTokensParticle) expectedObject;
		ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

		assertEquals(expected.getAddress(), argsExtractor.asRadixAddress("address"));
		assertEquals(expected.getAmount(), argsExtractor.asUInt256("amount"));
		assertEquals(expected.getGranularity(), argsExtractor.asUInt256("granularity"));
		assertEquals(expected.getTokDefRef(), argsExtractor.asRRI("tokenDefinitionReference"));
		assertEquals(expected.getNonce(), argsExtractor.asLong("nonce"));
		assertEquals(expected.getTokenPermissions(), argsExtractor.extractTokenPermissions("tokenPermissions"));

		assertTrue(argsExtractor.isFinished());

	}

	private static void assertFixedSupplyTokenDefinitionParticle(final Map<String, Object> arguments, final Object expectedObject) {
		FixedSupplyTokenDefinitionParticle expected = (FixedSupplyTokenDefinitionParticle) expectedObject;
		ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

		assertEquals(expected.getGranularity(), argsExtractor.asUInt256("granularity"));
		assertEquals(expected.getRRI(), argsExtractor.asRRI("rri"));
		assertEquals(expected.getName(), argsExtractor.asString("name"));
		assertEquals(expected.getDescription(), argsExtractor.asString("description"));
		assertEquals(expected.getIconUrl(), argsExtractor.asString("iconUrl"));
		assertEquals(expected.getSupply(), argsExtractor.asUInt256("supply"));
		assertEquals(expected.getUrl(), argsExtractor.asString("url"));

		assertTrue(argsExtractor.isFinished());
	}

	private static void assertMutableSupplyTokenDefinitionParticle(final Map<String, Object> arguments, final Object expectedObject) {
		MutableSupplyTokenDefinitionParticle expected = (MutableSupplyTokenDefinitionParticle) expectedObject;
		ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

		assertEquals(expected.getGranularity(), argsExtractor.asUInt256("granularity"));
		assertEquals(expected.getRRI(), argsExtractor.asRRI("rri"));
		assertEquals(expected.getName(), argsExtractor.asString("name"));
		assertEquals(expected.getDescription(), argsExtractor.asString("description"));
		assertEquals(expected.getIconUrl(), argsExtractor.asString("iconUrl"));
		assertEquals(expected.getUrl(), argsExtractor.asString("url"));

		assertTrue(argsExtractor.isFinished());
	}

	private static void assertStakedTokensParticle(final Map<String, Object> arguments, final Object expectedObject) {
		StakedTokensParticle expected = (StakedTokensParticle) expectedObject;
		ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

		assertEquals(expected.getAmount(), argsExtractor.asUInt256("amount"));
		assertEquals(expected.getAddress(), argsExtractor.asRadixAddress("address"));
		assertEquals(expected.getGranularity(), argsExtractor.asUInt256("granularity"));
		assertEquals(expected.getTokenPermissions(), argsExtractor.extractTokenPermissions("tokenPermissions"));
		assertEquals(expected.getDelegateAddress(), argsExtractor.asRadixAddress("delegateAddress"));
		assertEquals(expected.getNonce(), argsExtractor.asLong("nonce"));
		assertEquals(expected.getTokDefRef(), argsExtractor.asRRI("tokenDefinitionReference"));

		assertTrue(argsExtractor.isFinished());
	}

	private static void assertUnallocatedTokensParticle(final Map<String, Object> arguments, final Object expectedObject) {
		UnallocatedTokensParticle expected = (UnallocatedTokensParticle) expectedObject;
		ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

		assertEquals(expected.getAmount(), argsExtractor.asUInt256("amount"));
		assertEquals(expected.getGranularity(), argsExtractor.asUInt256("granularity"));
		assertEquals(expected.getTokDefRef(), argsExtractor.asRRI("tokenDefinitionReference"));
		assertEquals(expected.getNonce(), argsExtractor.asLong("nonce"));
		assertEquals(expected.getTokenPermissions(), argsExtractor.extractTokenPermissions("tokenPermissions"));

		assertTrue(argsExtractor.isFinished());
	}

	private static void assertRRIParticle(final Map<String, Object> arguments, final Object expectedObject) {
		RRIParticle expected = (RRIParticle) expectedObject;
		ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

		assertTrue(argsExtractor.isFinished());
	}

	private static void assertRegisteredValidatorParticle(final Map<String, Object> arguments, final Object expectedObject) {
		RegisteredValidatorParticle expected = (RegisteredValidatorParticle) expectedObject;
		ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

		assertTrue(argsExtractor.isFinished());
	}

	private static void assertUnregisteredValidatorParticle(final Map<String, Object> arguments, final Object expectedObject) {
		UnregisteredValidatorParticle expected = (UnregisteredValidatorParticle) expectedObject;
		ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

		assertTrue(argsExtractor.isFinished());
	}

	private static void assertSystemParticle(final Map<String, Object> arguments, final Object expectedObject) {
		SystemParticle expected = (SystemParticle) expectedObject;
		ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

		assertTrue(argsExtractor.isFinished());
	}

}

