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

package com.radixdlt.sanitytestsuite.scenario.jsonparticles;

import com.google.gson.reflect.TypeToken;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.JSONFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.assertEquals;

public class JsonRadixParticlesTestScenarioRunner extends SanityTestScenarioRunner<JsonParticlesTestVector> {

	private static final Logger log = LogManager.getLogger();

	public String testScenarioIdentifier() {
		return "json_radix_particles";
	}

	@Override
	public TypeToken<JsonParticlesTestVector> typeOfVector() {
		return new TypeToken<>() {
		};
	}

	public void doRunTestVector(JsonParticlesTestVector testVector) throws AssertionError {
		String bundledJsonPretty = testVector.input.jsonString();

		Particle particle = null;
		try {
			particle = DefaultSerialization.getInstance().fromJson(bundledJsonPretty, testVector.input.metaData.particleClass());
		} catch (DeserializeException e) {
			throw new AssertionError("Failed to convert bundled JSON into Particle model.", e);
		}
		String serializedJson = DefaultSerialization.getInstance().toJson(particle, testVector.input.metaData.output());
		String serializedJsonPretty = JSONFormatter.sortPrettyPrintJSONString(serializedJson);
		assertEquals(bundledJsonPretty, serializedJsonPretty);
		String hashString = Bytes.toHexString(sha256Hash(serializedJsonPretty.getBytes(StandardCharsets.UTF_8)));
		assertEquals(testVector.expected.hashOfJSON, hashString);
	}
}
