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

package com.radixdlt.crypto;

import com.radixdlt.TestSetupUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import java.nio.charset.StandardCharsets;

public class ECKeyPairTest {
	@BeforeClass
	public static void beforeClass() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ECKeyPair.class)
				.withIgnoredFields("publicKey") // public key is derived from used private key.
				.verify();
	}

	@Test
	public void checkKeyIntegrity() throws CryptoException {
		final int iterations = 5000;

		for (int i = 0; i < iterations; i++) {
			ECKeyPair key = new ECKeyPair();

			byte[] priv = key.getPrivateKey();
			byte[] pub = key.getPublicKey().getBytes();

			key = new ECKeyPair(priv);

			Assert.assertArrayEquals(priv, key.getPrivateKey());
			Assert.assertArrayEquals(pub, key.getPublicKey().getBytes());
		}
	}

	@Test
	public void signAndVerify() throws CryptoException {
		final int iterations = 2000;
		String helloWorld = "Hello World";

		for (int i = 0; i < iterations; i++) {
			ECKeyPair key = new ECKeyPair();
			byte[] priv = key.getPrivateKey();
			byte[] pub = key.getPublicKey().getBytes();

			ECKeyPair keyPair = new ECKeyPair(priv);
			ECDSASignature signature = keyPair.sign(Hash.hash256(helloWorld.getBytes(StandardCharsets.UTF_8)));

			ECPublicKey pubkey = new ECPublicKey(pub);
			Assert.assertTrue(pubkey.verify(Hash.hash256(helloWorld.getBytes(StandardCharsets.UTF_8)), signature));
		}
	}

	@Test
	public void encryptAndDecrypt() throws CryptoException {
		final int iterations = 1000;
		String helloWorld = "Hello World";

		for (int i = 0; i < iterations; ++i) {
			ECKeyPair key = new ECKeyPair();
			byte[] priv = key.getPrivateKey();


			byte[] encrypted = key.getPublicKey().encrypt(helloWorld.getBytes(StandardCharsets.UTF_8));

			ECKeyPair newkey = new ECKeyPair(priv);
			Assert.assertArrayEquals(helloWorld.getBytes(StandardCharsets.UTF_8), newkey.decrypt(encrypted));
		}
	}

	@Test
	public void checkKeyPairEquals() {
		EqualsVerifier.forClass(ECKeyPair.class)
			.withIgnoredFields("publicKey") // Computed
			.verify();
	}

	@Test
	public void checkPublicKeyEquals() {
		EqualsVerifier.forClass(ECPublicKey.class)
			.withIgnoredFields("uid") // Computed and cached
			.verify();
	}
}