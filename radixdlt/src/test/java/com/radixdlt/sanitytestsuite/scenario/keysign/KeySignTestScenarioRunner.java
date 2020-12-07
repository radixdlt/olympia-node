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
