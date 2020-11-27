package com.radixdlt.sanitytestsuite.scenario.jsonparticles;

import com.google.gson.reflect.TypeToken;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;

public class JsonRadixParticlesTestScenarioRunner extends SanityTestScenarioRunner<JsonParticlesTestVector> {


	public String testScenarioIdentifier() {
		return "json_radix_particles";
	}

	@Override
	public TypeToken<JsonParticlesTestVector> typeOfVector() {
		return new TypeToken<>() {
		};
	}

	public void doRunTestVector(JsonParticlesTestVector testVector) throws AssertionError {

	}
}
