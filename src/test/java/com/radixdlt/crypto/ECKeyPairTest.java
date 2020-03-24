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

import com.radixdlt.crypto.encryption.EncryptedPrivateKey;
import com.radixdlt.TestSetupUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

public class ECKeyPairTest {
	@BeforeClass
	public static void beforeClass() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void from_engine___equalsContract() {
		EqualsVerifier.forClass(ECKeyPair.class)
				.withIgnoredFields("publicKey") // public key is derived from used private key.
				.withIgnoredFields("version") // only used for serialization
				.verify();
	}

	@Test
	public void from_engine___checkKeyIntegrity() throws CryptoException {
		final int iterations = 5000;

		for (int i = 0; i < iterations; i++) {
			ECKeyPair key = ECKeyPair.generateNew();

			byte[] priv = key.getPrivateKey();
			byte[] pub = key.getPublicKey().getBytes();

			key = new ECKeyPair(priv);

			Assert.assertArrayEquals(priv, key.getPrivateKey());
			Assert.assertArrayEquals(pub, key.getPublicKey().getBytes());
		}
	}

	@Test
	public void from_engine___signAndVerify() throws CryptoException {
		final int iterations = 2000;
		String helloWorld = "Hello World";

		for (int i = 0; i < iterations; i++) {
			ECKeyPair key = ECKeyPair.generateNew();
			byte[] priv = key.getPrivateKey();
			byte[] pub = key.getPublicKey().getBytes();

			ECKeyPair keyPair = new ECKeyPair(priv);
			ECDSASignature signature = keyPair.sign(Hash.hash256(helloWorld.getBytes(StandardCharsets.UTF_8)));

			ECPublicKey pubkey = new ECPublicKey(pub);
			assertTrue(pubkey.verify(Hash.hash256(helloWorld.getBytes(StandardCharsets.UTF_8)), signature));
		}
	}

	@Test
	public void from_engine___encryptAndDecrypt() throws CryptoException {
		final int iterations = 1000;
		String helloWorld = "Hello World";

		for (int i = 0; i < iterations; ++i) {
			ECKeyPair key = ECKeyPair.generateNew();
			byte[] priv = key.getPrivateKey();


			byte[] encrypted = key.getPublicKey().encrypt(helloWorld.getBytes(StandardCharsets.UTF_8));

			ECKeyPair newkey = new ECKeyPair(priv);
			Assert.assertArrayEquals(helloWorld.getBytes(StandardCharsets.UTF_8), newkey.decrypt(encrypted));
		}
	}

	@Test
	public void from_engine___checkKeyPairEquals() {
		EqualsVerifier.forClass(ECKeyPair.class)
			.withIgnoredFields("publicKey") // Computed
				.withIgnoredFields("version") // only used for serialization
			.verify();
	}

	@Test
	public void from_engine___checkPublicKeyEquals() {
		EqualsVerifier.forClass(ECPublicKey.class)
			.withIgnoredFields("uid") // Computed and cached
			.verify();
	}

	// ### FROM Client Librart ###

	@Test
	public void decrypt_bad_encrypted_data_with_good_encrypted_private_key__should_throw_CryptoException() throws CryptoException {
		ECKeyPair keyPair1 = ECKeyPair.generateNew();
		ECKeyPair keyPair2 = ECKeyPair.generateNew();

		EncryptedPrivateKey encryptedPrivateKey = keyPair2.encryptPrivateKeyWithPublicKey(keyPair1.getPublicKey()); //(keyPair1.getPublicKey());

		assertThatThrownBy(() -> keyPair1.decrypt(new byte[]{0}, encryptedPrivateKey))
				.isInstanceOf(CryptoException.class);
	}

	@Test
	public void encryptionTest() throws CryptoException {
		String testPhrase = "Hello World";
		ECKeyPair ecKeyPair = ECKeyPair.generateNew();
		byte[] encrypted = ecKeyPair.getPublicKey().encrypt(testPhrase.getBytes());
		assertTrue(encrypted.length > 0);
		byte[] decrypted = ecKeyPair.decrypt(encrypted);
		assertEquals(testPhrase, new String(decrypted));
	}


	@Test
	public void when_generating_two_default_key_pairs__they_should_have_different_private_keys() throws CryptoException {
		byte[] privateKey1 = ECKeyPair.generateNew().getPrivateKey();
		byte[] privateKey2 = ECKeyPair.generateNew().getPrivateKey();

		assertThat(privateKey1, not(equalTo(privateKey2)));
	}

	@Test
	public void when_generating_two_key_pairs_from_same_seed__they_should_have_same_private_keys() throws CryptoException {
		byte[] seed = "seed".getBytes();
		byte[] privateKey1 = ECKeyPair.fromSeedSha2bits256HashedTwice(seed).getPrivateKey();
		byte[] privateKey2 = ECKeyPair.fromSeedSha2bits256HashedTwice(seed).getPrivateKey();

		assertThat(privateKey1, equalTo(privateKey2));
	}

	@Test
	public void when_signing_some_hash_with_a_seeded_key_pair__another_key_pair_from_same_seed_can_verify_the_signature() throws CryptoException {
		byte[] seed = "seed".getBytes();
		ECKeyPair keyPair1 = ECKeyPair.fromSeedSha2bits256HashedTwice(seed);
		ECKeyPair keyPair2 = ECKeyPair.fromSeedSha2bits256HashedTwice(seed);

		Hash hash1 = Hash.random();
		Hash hash2 = Hash.random();
		ECDSASignature signature1 = keyPair1.sign(hash1);
		ECDSASignature signature2 = keyPair2.sign(hash2);

		// Assert that KeyPair1 can be used to verify the signature of Hash2
		assertTrue(keyPair1.getPublicKey().verify(hash2, signature2));

		// Assert that KeyPair2 can be used to verify the signature of Hash1
		assertTrue(keyPair2.getPublicKey().verify(hash1, signature1));
	}
}