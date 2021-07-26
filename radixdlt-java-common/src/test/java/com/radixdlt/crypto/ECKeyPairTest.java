/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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