package com.radixdlt.sanitytestsuite.scenario.keysign;

import com.google.gson.reflect.TypeToken;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.utils.Bytes;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class KeySignTestScenarioRunner extends SanityTestScenarioRunner<KeySignTestVector> {


	public String testScenarioIdentifier() {
		return "ecdsa_signing";
	}

	@Override
	public TypeToken<KeySignTestVector> typeOfVector() {
		return new TypeToken<>() {
		};
	}

	public void doRunTestVector(KeySignTestVector testVector) throws AssertionError {
		ECKeyPair keyPair = null;
		try {
			keyPair = ECKeyPair.fromPrivateKey(Bytes.fromHexString(testVector.input.privateKey));
		} catch (Exception e) {
			throw new AssertionError("Failed to construct private key from hex", e);
		}

		byte[] unhashedEncodedMessage = testVector.input.messageToSign.getBytes(StandardCharsets.UTF_8);
		byte[] hashedMessageToSign = sha256Hash(unhashedEncodedMessage);
		ECDSASignature signature = keyPair.sign(hashedMessageToSign, true, true);
		assertEquals(
				testVector.expected.signature.r,
				signature.getR().toString(16)
		);
		assertEquals(
				testVector.expected.signature.s,
				signature.getS().toString(16)
		);

	}
}
