package com.radixdlt.sanitytestsuite.scenario.hashing;

import com.google.gson.reflect.TypeToken;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.utils.Bytes;

import static org.junit.Assert.assertEquals;

public class HashingTestScenarioRunner extends SanityTestScenarioRunner<HashingTestVector> {


	public String testScenarioIdentifier() {
		return "hashing";
	}

	@Override
	public TypeToken<HashingTestVector> typeOfVector() {
		return new TypeToken<>() {
		};
	}

	public void doRunTestVector(HashingTestVector testVector) throws AssertionError {
		String hashHex = Bytes.toHexString(sha256Hash(testVector.input.bytesToHash()));

		assertEquals(testVector.expected.hash, hashHex);

	}
}
