package com.radixdlt.sanitytestsuite.scenario.radixhashing;

import com.google.gson.reflect.TypeToken;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.utils.Bytes;

import static org.junit.Assert.assertEquals;

public class RadixHashingTestScenarioRunner extends SanityTestScenarioRunner<RadixHashingTestVector> {

	public String testScenarioIdentifier() {
		return "radix_hashing";
	}

	@Override
	public TypeToken<RadixHashingTestVector> typeOfVector() {
		return new TypeToken<>() {
		};
	}

	public void doRunTestVector(RadixHashingTestVector testVector) throws AssertionError {
		String hashHex = Bytes.toHexString(HashUtils.sha256(testVector.input.bytesToHash()).asBytes());
		assertEquals(testVector.expected.hashOfHash, hashHex);

	}
}
