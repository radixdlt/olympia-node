package com.radixdlt.sanitytestsuite.scenario.keygen;


import com.google.gson.reflect.TypeToken;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.utils.Bytes;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;

public class KeyGenTestScenarioRunner extends SanityTestScenarioRunner<KeyGenTestVector> {


	public String testScenarioIdentifier() {
		return "secp256k1";
	}

	@Override
	public TypeToken<KeyGenTestVector> typeOfVector() {
		return new TypeToken<>() {
		};
	}

	public void doRunTestVector(KeyGenTestVector testVector) throws AssertionError {
		ECPublicKey publicKey = null;
		ECPublicKey expectedPublicKey = null;

		try {
			publicKey = ECKeyPair.fromPrivateKey(Bytes.fromHexString(testVector.input.privateKey)).getPublicKey();
			expectedPublicKey = ECPublicKey.fromBytes(Bytes.fromHexString(testVector.expected.uncompressedPublicKey));
		} catch (Exception e) {
			throw new AssertionError("Failed to create PublicKeys", e);
		}

		assertNotNull(publicKey);
		assertTrue(publicKey.equals(expectedPublicKey));

	}
}
