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
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
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

    @Override
    public String testScenarioIdentifier() {
        return "serialization_radix_models";
    }

    @Override
    public Class<SerializationTestVector> testVectorType() {
        return SerializationTestVector.class;
    }

    public SerializationTestScenarioRunner() {

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

    private static final Map<String, Function<Map<String, Object>, Object>> constructorMap = ImmutableMap.of(
            "radix.particles.transferrable_tokens", SerializationTestScenarioRunner::makeTransferrableTokensParticle
    );

    @Override
    public void doRunTestVector(SerializationTestVector testVector) throws AssertionError {
        /*
        var produced = ofNullable(constructorMap.get(testVector.input.typeSerialization))
                .map(constructor -> constructor.apply(testVector.input.arguments))
                .map(model -> {
                    String expectedDsonAllHex = testVector.expected.dson.get("allOutputHex").toString();
                    String expectedDsonHashHex = testVector.expected.dson.get("hashOutputHex").toString();
                    String dsonAllHex = Hex.toHexString(serialization.toDson(model, DsonOutput.Output.ALL));
                    String dsonHashHex = Hex.toHexString(serialization.toDson(model, DsonOutput.Output.HASH));
                    assertEquals("DSON (all) mismatch", expectedDsonAllHex, dsonAllHex);
                    assertEquals("DSON (hash) mismatch", expectedDsonHashHex, dsonHashHex);
                    return model;
                })
                .map(model -> serialization.toJson(model, DsonOutput.Output.HASH))
                .map(JSONFormatter::sortPrettyPrintJSONString)
                .orElseThrow(() -> new IllegalStateException("Cant find constructor for " + testVector.input.typeSerialization));
        String expected = JSONFormatter.sortPrettyPrintJSONString(testVector.expected.jsonPrettyPrinted);
        assertEquals(expected, produced);*/
    }

}