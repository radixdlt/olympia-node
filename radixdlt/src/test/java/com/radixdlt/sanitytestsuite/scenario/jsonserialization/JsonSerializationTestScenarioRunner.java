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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.JSONFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.function.Function;

import static java.util.Optional.ofNullable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class JsonSerializationTestScenarioRunner extends SanityTestScenarioRunner<JsonSerializationTestVector> {
	private static final Logger LOG = LogManager.getLogger();

	private final ObjectMapper mapper = new ObjectMapper();
	private final Serialization serialization = DefaultSerialization.getInstance();

	public String testScenarioIdentifier() {
		return "json_serialization_radix_particles";
	}

	@Override
	public Class<JsonSerializationTestVector> testVectorType() {
		return JsonSerializationTestVector.class;
	}

	private static TransferrableTokensParticle makeTTP(final Map<String, Object> arguments) {
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

	private static Map<String, Function<Map<String, Object>, Object>> constructorMap = ImmutableMap.of(
		"radix.particles.transferrable_tokens", JsonSerializationTestScenarioRunner::makeTTP
	);

	public void doRunTestVector(JsonSerializationTestVector testVector) throws AssertionError {
		var produced = ofNullable(constructorMap.get(testVector.input.typeSerialization))
			.map(constructor -> constructor.apply(testVector.input.arguments))
			.map(model -> serialization.toJson(model, DsonOutput.Output.HASH))
			.map(JSONFormatter::sortPrettyPrintJSONString)
			.orElseThrow(() -> new IllegalStateException("Cant find constructor"));

		LOG.info("🧩🧩🧩 produced: {}", produced);

		String expected = JSONFormatter.sortPrettyPrintJSONString(testVector.expected.jsonPrettyPrinted);

		assertEquals(expected, produced);
	}
}