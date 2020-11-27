package com.radixdlt.sanitytestsuite.scenario.keyverify;

import com.google.gson.reflect.TypeToken;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.utils.Bytes;

import static org.junit.Assert.assertEquals;

public class KeyVerifyTestScenarioRunner extends SanityTestScenarioRunner<KeyVerifyTestVector> {


	public String testScenarioIdentifier() {
		return "ecdsa_verification";
	}

	@Override
	public TypeToken<KeyVerifyTestVector> typeOfVector() {
		return new TypeToken<>() {
		};
	}

	public void doRunTestVector(KeyVerifyTestVector testVector) throws AssertionError {

		ECPublicKey publicKey = null;
		try {
			publicKey = ECPublicKey.fromBytes(Bytes.fromHexString(testVector.input.publicKeyUncompressed));
		} catch (Exception e) {
			throw new AssertionError("Failed to construct public key from hex", e);
		}
		ECDSASignature signature = ECDSASignature.decodeFromDER(Bytes.fromHexString(testVector.input.signatureDerEncoded));

		byte[] hashedMessageToVerify = sha256Hash(Bytes.fromHexString(testVector.input.msg));

		assertEquals(
				testVector.expected.isValid,
				publicKey.verify(hashedMessageToVerify, signature)
		);
	}
}
