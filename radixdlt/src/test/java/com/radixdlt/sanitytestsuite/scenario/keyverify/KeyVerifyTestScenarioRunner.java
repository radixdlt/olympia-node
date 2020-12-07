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
