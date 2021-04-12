/*
 * (C) Copyright 2021 Radix DLT Ltd
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
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.validators.ValidatorParticle;
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
import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DeserializationTestScenarioRunner extends SanityTestScenarioRunner<DeserializationTestVector> {

    private static final Serialization serialization = DefaultSerialization.getInstance();
    private final ObjectMapper mapper = new ObjectMapper();

    private final ImmutableMap<String, BiConsumer<Map<String, Object>, Object>> assertEqualsMap;

    public DeserializationTestScenarioRunner() {
        var mutableMap = Maps.<String, BiConsumer<Map<String, Object>, Object>>newHashMap();
        mutableMap.put("radix.particles.transferrable_tokens", DeserializationTestScenarioRunner::assertTransferableTokensParticle);
        mutableMap.put("radix.particles.fixed_supply_token_definition", DeserializationTestScenarioRunner::assertFixedSupplyTokenDefinitionParticle);
        mutableMap.put("radix.particles.mutable_supply_token_definition",
                DeserializationTestScenarioRunner::assertMutableSupplyTokenDefinitionParticle);
        mutableMap.put("radix.particles.staked_tokens", DeserializationTestScenarioRunner::assertStakedTokensParticle);
        mutableMap.put("radix.particles.rri", DeserializationTestScenarioRunner::assertRRIParticle);
        mutableMap.put("radix.particles.registered_validator", DeserializationTestScenarioRunner::assertRegisteredValidatorParticle);
        mutableMap.put("radix.particles.system_particle", DeserializationTestScenarioRunner::assertSystemParticle);
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
        var assertEqualModel = assertEqualsMap.get(testVector.input.typeSerialization);
        assertNotNull(
                "Serializer must be implemented for " + testVector.input.typeSerialization,
                assertEqualModel
        );
        Class<?> type = serialization.getClassForId(testVector.input.typeSerialization);
        assertNotNull(
                "Unknown or missing serialization type: " + type,
                type
        );

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
                        return serialization.fromJson(jsonString, type);
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

    private static void assertEqualDson(DeserializationTestVector testVector, Object deserializedModel,
                                        Class<? extends Particle> classToDeserializeAs) {
        try {
            byte[] dsonAllBytes = Hex.decode(testVector.input.dson.get("all").toString());
            Particle deserializedParticle = serialization.fromDson(dsonAllBytes, classToDeserializeAs);
            assertEquals(deserializedModel, deserializedParticle);
        } catch (DeserializeException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertTransferableTokensParticle(final Map<String, Object> arguments, final Object expectedObject) {
        TokensParticle expected = (TokensParticle) expectedObject;
        ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

        assertEquals(expected.getAddress(), argsExtractor.asRadixAddress("address"));
        assertEquals(expected.getAmount(), argsExtractor.asUInt256("amount"));
        assertEquals(expected.getTokDefRef(), argsExtractor.asRRI("tokenDefinitionReference"));

        assertTrue(argsExtractor.isFinished());
    }

    private static void assertFixedSupplyTokenDefinitionParticle(final Map<String, Object> arguments, final Object expectedObject) {
        FixedSupplyTokenDefinitionParticle expected = (FixedSupplyTokenDefinitionParticle) expectedObject;
        ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

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
        assertEquals(expected.getDelegateAddress(), argsExtractor.asRadixAddress("delegateAddress"));

        assertTrue(argsExtractor.isFinished());
    }

    private static void assertRRIParticle(final Map<String, Object> arguments, final Object expectedObject) {
        RRIParticle expected = (RRIParticle) expectedObject;
        ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

        assertEquals(expected.getRri(), argsExtractor.asRRI("rri"));

        assertTrue(argsExtractor.isFinished());
    }

    private static void assertRegisteredValidatorParticle(final Map<String, Object> arguments, final Object expectedObject) {
        ValidatorParticle expected = (ValidatorParticle) expectedObject;
        ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

        assertEquals(expected.getAddress(), argsExtractor.asRadixAddress("address"));

        assertTrue(argsExtractor.isFinished());
    }

    private static void assertSystemParticle(final Map<String, Object> arguments, final Object expectedObject) {
        SystemParticle expected = (SystemParticle) expectedObject;
        ArgumentsExtractor argsExtractor = ArgumentsExtractor.from(arguments);

        assertEquals(expected.getView(), argsExtractor.asLong("view"));
        assertEquals(expected.getEpoch(), argsExtractor.asLong("epoch"));
        assertEquals(expected.getTimestamp(), argsExtractor.asLong("timestamp"));

        assertTrue(argsExtractor.isFinished());
    }

}