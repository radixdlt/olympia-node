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

import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;

import static com.radixdlt.utils.Bytes.fromHexString;
import static org.junit.Assert.assertEquals;

public final class KeyVerifyTestScenarioRunner extends SanityTestScenarioRunner<KeyVerifyTestVector> {
	@Override
	public String testScenarioIdentifier() {
		return "ecdsa_verification";
	}

	@Override
	public Class<KeyVerifyTestVector> testVectorType() {
		return KeyVerifyTestVector.class;
	}

	@Override
	public void doRunTestVector(KeyVerifyTestVector testVector) throws AssertionError {

		ECPublicKey publicKey = null;
		try {
			publicKey = ECPublicKey.fromBytes(fromHexString(testVector.input.publicKeyUncompressed));
		} catch (Exception e) {
			throw new AssertionError("Failed to construct public key from hex", e);
		}
		ECDSASignature signature = ECDSASignature.decodeFromDER(fromHexString(testVector.input.signatureDerEncoded));

		byte[] hashedMessageToVerify = sha256Hash(fromHexString(testVector.input.msg));

		assertEquals(
			testVector.expected.isValid,
			publicKey.verify(hashedMessageToVerify, signature)
		);
	}
}
