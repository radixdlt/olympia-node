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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.hash.HashCode;
import com.radixdlt.TestSetupUtils;
import com.radixdlt.crypto.encryption.EncryptedPrivateKey;
import com.radixdlt.crypto.exception.ECIESException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import nl.jqno.equalsverifier.EqualsVerifier;

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
	public void checkKeyIntegrity() throws Exception {
		final int iterations = 5000;

		for (int i = 0; i < iterations; i++) {
			ECKeyPair key = ECKeyPair.generateNew();

			byte[] priv = key.getPrivateKey();
			byte[] pub = key.getPublicKey().getBytes();

			key = ECKeyPair.fromPrivateKey(priv);

			assertArrayEquals(priv, key.getPrivateKey());
			assertArrayEquals(pub, key.getPublicKey().getBytes());
		}
	}

	@Test
	public void signAndVerify() throws Exception {
		final int iterations = 2000;
		String helloWorld = "Hello World";

		for (int i = 0; i < iterations; i++) {
			ECKeyPair key = ECKeyPair.generateNew();
			byte[] priv = key.getPrivateKey();
			byte[] pub = key.getPublicKey().getBytes();

			ECKeyPair keyPair = ECKeyPair.fromPrivateKey(priv);
			ECDSASignature signature = keyPair.sign(HashUtils.sha256(helloWorld.getBytes(StandardCharsets.UTF_8)).asBytes());

			ECPublicKey pubkey = ECPublicKey.fromBytes(pub);
			assertTrue(pubkey.verify(HashUtils.sha256(helloWorld.getBytes(StandardCharsets.UTF_8)).asBytes(), signature));
		}
	}

	@Test
	public void encryptAndDecrypt() throws Exception {
		final int iterations = 1000;
		String helloWorld = "Hello World";

		for (int i = 0; i < iterations; ++i) {
			ECKeyPair key = ECKeyPair.generateNew();
			byte[] priv = key.getPrivateKey();


			byte[] encrypted = key.getPublicKey().encrypt(helloWorld.getBytes(StandardCharsets.UTF_8));

			ECKeyPair newkey = ECKeyPair.fromPrivateKey(priv);
			assertArrayEquals(helloWorld.getBytes(StandardCharsets.UTF_8), newkey.decrypt(encrypted));
		}
	}

	@Test
	public void checkKeyPairEquals() {
		EqualsVerifier.forClass(ECKeyPair.class)
			.withIgnoredFields("publicKey") // Computed
			.verify();
	}

	@Test
	public void decrypt_bad_encrypted_data_with_good_encrypted_private_key__should_throw_CryptoException() {
		ECKeyPair keyPair1 = ECKeyPair.generateNew();
		ECKeyPair keyPair2 = ECKeyPair.generateNew();

		EncryptedPrivateKey encryptedPrivateKey = keyPair2.encryptPrivateKeyWithPublicKey(keyPair1.getPublicKey());

		assertThatThrownBy(() -> keyPair1.decrypt(new byte[]{0}, encryptedPrivateKey))
				.isInstanceOf(ECIESException.class);
	}

	@Test
	public void encryptionTest() throws ECIESException {
		String testPhrase = "Hello World";
		ECKeyPair ecKeyPair = ECKeyPair.generateNew();
		byte[] encrypted = ecKeyPair.getPublicKey().encrypt(testPhrase.getBytes());
		assertTrue(encrypted.length > 0);
		byte[] decrypted = ecKeyPair.decrypt(encrypted);
		assertEquals(testPhrase, new String(decrypted));
	}


	@Test
	public void when_generating_two_default_key_pairs__they_should_have_different_private_keys() {
		byte[] privateKey1 = ECKeyPair.generateNew().getPrivateKey();
		byte[] privateKey2 = ECKeyPair.generateNew().getPrivateKey();

		assertThat(privateKey1).isNotEqualTo(privateKey2);
	}

	@Test
	public void when_generating_two_key_pairs_from_same_seed__they_should_have_same_private_keys() {
		byte[] seed = "seed".getBytes();
		byte[] privateKey1 = ECKeyPair.fromSeed(seed).getPrivateKey();
		byte[] privateKey2 = ECKeyPair.fromSeed(seed).getPrivateKey();

		assertThat(privateKey1).isEqualTo(privateKey2);
	}

	@Test
	public void when_signing_some_hash_with_a_seeded_key_pair__another_key_pair_from_same_seed_can_verify_the_signature() {
		byte[] seed = "seed".getBytes();
		ECKeyPair keyPair1 = ECKeyPair.fromSeed(seed);
		ECKeyPair keyPair2 = ECKeyPair.fromSeed(seed);

		HashCode hash1 = HashUtils.random256();
		HashCode hash2 = HashUtils.random256();
		ECDSASignature signature1 = keyPair1.sign(hash1);
		ECDSASignature signature2 = keyPair2.sign(hash2);

		// Assert that KeyPair1 can be used to verify the signature of Hash2
		assertTrue(keyPair1.getPublicKey().verify(hash2, signature2));

		// Assert that KeyPair2 can be used to verify the signature of Hash1
		assertTrue(keyPair2.getPublicKey().verify(hash1, signature1));
	}

	@Test
	public void validateSeedBeforeUse() {
		assertThatThrownBy(() -> ECKeyPair.fromSeed(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> ECKeyPair.fromSeed(new byte[]{})).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void keyPairCanBeLoadedFromFile() throws IOException, PrivateKeyException, PublicKeyException {
		File testKeyPair = new File("test-private-key.ks");
		var sourceKeyPair = ECKeyPair.generateNew();

		try (OutputStream outputStream = new FileOutputStream(testKeyPair)) {
			outputStream.write(sourceKeyPair.getPrivateKey());
		}

		var loadedKeyPair = ECKeyPair.fromFile(testKeyPair);

		assertThat(sourceKeyPair.getPrivateKey()).isEqualTo(loadedKeyPair.getPrivateKey());
	}

	@Test
	public void shortFileIsRejected() throws IOException {
		File testKeyPair = new File("test-private-key.ks");

		try (OutputStream outputStream = new FileOutputStream(testKeyPair)) {
			outputStream.write(new byte[ECKeyPair.BYTES - 1]);
		}

		assertThatThrownBy(() -> ECKeyPair.fromFile(testKeyPair)).isInstanceOf(IllegalStateException.class);
	}
}