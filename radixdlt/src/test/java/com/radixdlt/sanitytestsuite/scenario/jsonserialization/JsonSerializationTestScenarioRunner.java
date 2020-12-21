

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.utils.JSONFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class JsonSerializationTestScenarioRunner extends SanityTestScenarioRunner<JsonSerializationTestVector> {

	private static final Logger log = LogManager.getLogger();

	public String testScenarioIdentifier() {
		return "json_serialization_radix_particles";
	}

	@Override
	public TypeReference<JsonSerializationTestVector> testVectorTypeReference() {
		return new TypeReference<JsonSerializationTestVector>() {
		};
	}

	private static TransferrableTokensParticle makeTTP(JsonNode jsonNode) {
		ArgumentsExtractor argsExtractor = new ArgumentsExtractor(jsonNode);

		TransferrableTokensParticle ttp = new TransferrableTokensParticle(
			argsExtractor.extractRadixAddress("address"),
			argsExtractor.extractUInt256("amount"),
			argsExtractor.extractUInt256("granularity"),
			argsExtractor.extractRRI("tokenDefinitionReference"),
			argsExtractor.extractTokenPermissions("tokenPermissions"),
			argsExtractor.extractLong("nonce")
		);

		assertTrue(argsExtractor.isFinished());

		return ttp;

	}

	private static Map<String, Function<JsonNode, Object>> constructorMap = ImmutableMap.of(
		"radix.particles.transferrable_tokens", JsonSerializationTestScenarioRunner::makeTTP
	);

	public void doRunTestVector(JsonSerializationTestVector testVector) throws AssertionError {
		Function<JsonNode, Object> constructor = constructorMap.get(testVector.input.typeSerialization);

		if (constructor == null) {
			throw new IllegalStateException("Cant find constructor");
		}

		Object argumentsObj = testVector.input.arguments;
		ObjectMapper mapper = new ObjectMapper();
		String jsonString = null;
		try {
			jsonString = mapper.writeValueAsString(argumentsObj);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to write obj to JSON");
		}

		JsonNode jsonNode = null;
		try {
			jsonNode = mapper.readTree(jsonString);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to write obj to JSON");
		}


		Object modelToSerialize = constructor.apply(jsonNode);

		if (modelToSerialize == null) {
			throw new IllegalStateException("Cant find input args for constructor");
		}

		String produced = DefaultSerialization.getInstance().toJson(modelToSerialize, DsonOutput.Output.HASH);

		log.error(String.format("🧩🧩🧩 produced: %s", produced));

		String expected = testVector.expected.jsonPrettyPrinted;

		assertEquals(JSONFormatter.sortPrettyPrintJSONString(expected), JSONFormatter.sortPrettyPrintJSONString(produced));
	}
}