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

package com.radixdlt.sanitytestsuite.scenario.serialization;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.*;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.sanitytestsuite.utility.ArgumentsExtractor;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.JSONFormatter;
import org.bouncycastle.util.encoders.Hex;

import java.util.Map;
import java.util.function.Function;

import static java.util.Optional.ofNullable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SerializationTestScenarioRunner extends SanityTestScenarioRunner<SerializationTestVector> {

    private final Serialization serialization = DefaultSerialization.getInstance();

    private final Map<String, Function<Map<String, Object>, Object>> constructorMap;

    public SerializationTestScenarioRunner() {
        Map<String, Function<Map<String, Object>, Object>> mutableMap = Maps.newHashMap();
        mutableMap.put("radix.particles.transferrable_tokens", SerializationTestScenarioRunner::makeTransferrableTokensParticle);
        mutableMap.put("radix.particles.fixed_supply_token_definition", SerializationTestScenarioRunner::makeFixedSupplyTokenDefinitionParticle);
        mutableMap.put("radix.particles.mutable_supply_token_definition",
                SerializationTestScenarioRunner::makeMutableSupplyTokenDefinitionParticle);
        mutableMap.put("radix.particles.staked_tokens", SerializationTestScenarioRunner::makeStakedTokensParticle);
        mutableMap.put("radix.particles.unallocated_tokens", SerializationTestScenarioRunner::makeUnallocatedTokensParticle);
        mutableMap.put("radix.particles.rri", SerializationTestScenarioRunner::makeRRIParticle);
        mutableMap.put("radix.particles.registered_validator", SerializationTestScenarioRunner::makeRegisteredValidatorParticle);
        mutableMap.put("radix.particles.unregistered_validator", SerializationTestScenarioRunner::makeUnregisteredValidatorParticle);
        mutableMap.put("radix.particles.system_particle", SerializationTestScenarioRunner::makeSystemParticle);
        constructorMap = ImmutableMap.copyOf(mutableMap);
    }

    @Override
    public String testScenarioIdentifier() {
        return "serialization_radix_models";
    }

    @Override
    public Class<SerializationTestVector> testVectorType() {
        return SerializationTestVector.class;
    }

    private static FixedSupplyTokenDefinitionParticle makeFixedSupplyTokenDefinitionParticle(final Map<String, Object> arguments) {
        var argsExtractor = ArgumentsExtractor.from(arguments);
        var ttp = new FixedSupplyTokenDefinitionParticle(
                argsExtractor.asRRI("rri"),
                argsExtractor.asString("name"),
                argsExtractor.asString("description"),
                argsExtractor.asUInt256("supply"),
                argsExtractor.asUInt256("granularity"),
                argsExtractor.asString("iconUrl"),
                argsExtractor.asString("url")
        );
        assertTrue(argsExtractor.isFinished());
        return ttp;
    }

    private static MutableSupplyTokenDefinitionParticle makeMutableSupplyTokenDefinitionParticle(final Map<String, Object> arguments) {
        var argsExtractor = ArgumentsExtractor.from(arguments);
        var ttp = new MutableSupplyTokenDefinitionParticle(
                argsExtractor.asRRI("rri"),
                argsExtractor.asString("name"),
                argsExtractor.asString("description"),
                argsExtractor.asUInt256("granularity"),
                argsExtractor.asString("iconUrl"),
                argsExtractor.asString("url"),
                argsExtractor.extractTokenPermissions("permissions")
        );
        assertTrue(argsExtractor.isFinished());
        return ttp;
    }

    private static StakedTokensParticle makeStakedTokensParticle(final Map<String, Object> arguments) {
        var argsExtractor = ArgumentsExtractor.from(arguments);
        var ttp = new StakedTokensParticle(
                argsExtractor.asRadixAddress("delegateAddress"),
                argsExtractor.asRadixAddress("address"),
                argsExtractor.asUInt256("amount"),
                argsExtractor.asUInt256("granularity"),
                argsExtractor.asRRI("tokenDefinitionReference"),
                argsExtractor.extractTokenPermissions("permissions"),
                argsExtractor.asLong("nonce")
        );

        assertTrue(argsExtractor.isFinished());
        return ttp;
    }

    private static TransferrableTokensParticle makeTransferrableTokensParticle(final Map<String, Object> arguments) {
        var argsExtractor = ArgumentsExtractor.from(arguments);
        var ttp = new TransferrableTokensParticle(
                argsExtractor.asRadixAddress("address"),
                argsExtractor.asUInt256("amount"),
                argsExtractor.asUInt256("granularity"),
                argsExtractor.asRRI("tokenDefinitionReference"),
                argsExtractor.extractTokenPermissions("tokenPermissions"),
                argsExtractor.asLong("nonce")
        );
        assertTrue(argsExtractor.isFinished());
        return ttp;
    }

    private static UnallocatedTokensParticle makeUnallocatedTokensParticle(final Map<String, Object> arguments) {
        var argsExtractor = ArgumentsExtractor.from(arguments);
        var utp = new UnallocatedTokensParticle(
                argsExtractor.asUInt256("amount"),
                argsExtractor.asUInt256("granularity"),
                argsExtractor.asRRI("tokenDefinitionReference"),
                argsExtractor.extractTokenPermissions("permissions"),
                argsExtractor.asLong("nonce")
        );
        assertTrue(argsExtractor.isFinished());
        return utp;
    }

    private static RRIParticle makeRRIParticle(final Map<String, Object> arguments) {
        var argsExtractor = ArgumentsExtractor.from(arguments);
        var rrip = new RRIParticle(
                argsExtractor.asRRI("rri"),
                argsExtractor.asLong("nonce")
        );
        assertTrue(argsExtractor.isFinished());
        return rrip;
    }

    private static RegisteredValidatorParticle makeRegisteredValidatorParticle(final Map<String, Object> arguments) {
        var argsExtractor = ArgumentsExtractor.from(arguments);
        var rvp = new RegisteredValidatorParticle(
                argsExtractor.asRadixAddress("address"),
                ImmutableSet.copyOf(argsExtractor.asAddressSet("allowedDelegators")),
                argsExtractor.asLong("nonce")
        );
        assertTrue(argsExtractor.isFinished());
        return rvp;
    }

    private static UnregisteredValidatorParticle makeUnregisteredValidatorParticle(final Map<String, Object> arguments) {
        var argsExtractor = ArgumentsExtractor.from(arguments);
        var uvp = new UnregisteredValidatorParticle(
                argsExtractor.asRadixAddress("address"),
                argsExtractor.asLong("nonce")
        );
        assertTrue(argsExtractor.isFinished());
        return uvp;
    }

    private static SystemParticle makeSystemParticle(final Map<String, Object> arguments) {
        var argsExtractor = ArgumentsExtractor.from(arguments);
        var sp = new SystemParticle(
                argsExtractor.asLong("epoch"),
                argsExtractor.asLong("view"),
                argsExtractor.asLong("timestamp")
        );
        assertTrue(argsExtractor.isFinished());
        return sp;
    }

    @Override
    public void doRunTestVector(SerializationTestVector testVector) throws AssertionError {
        var produced = ofNullable(constructorMap.get(testVector.input.typeSerialization))
                .map(constructor -> constructor.apply(testVector.input.arguments))
                .map(model -> {
                    String expectedDsonAllHex = testVector.expected.dson.get("all").toString();
                    String dsonAllHex = Hex.toHexString(serialization.toDson(model, DsonOutput.Output.ALL));
                    assertEquals("DSON (all) mismatch", expectedDsonAllHex, dsonAllHex);
                    return model;
                })
                .map(model -> serialization.toJson(model, DsonOutput.Output.HASH))
                .map(JSONFormatter::sortPrettyPrintJSONString)
                .orElseThrow(() -> new IllegalStateException("Cant find constructor for " + testVector.input.typeSerialization));
        String expected = JSONFormatter.sortPrettyPrintJSONString(testVector.expected.jsonPrettyPrinted);
        assertEquals(expected, produced);
    }

}