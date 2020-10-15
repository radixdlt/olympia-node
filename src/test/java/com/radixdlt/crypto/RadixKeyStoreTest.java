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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.stream.IntStream;

import javax.crypto.SecretKey;

import com.radixdlt.crypto.exception.KeyStoreException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import org.bouncycastle.jcajce.PKCS12Key;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import com.radixdlt.TestSetupUtils;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.*;

public class RadixKeyStoreTest {

	private static final String TEST_SECRET = "secret";
	private static final String TEST_KS_FILENAME = "testfile.ks";

	@BeforeClass
	public static void setup() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	/**
	 * Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}.
	 */
	@Test
	public void testFromFileCreate() throws IOException, KeyStoreException {
		File file = newFile(TEST_KS_FILENAME);
		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
			assertTrue(file.exists());
			assertTrue(ks.toString().contains(file.toString()));
		}
	}

	/**
	 * Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}.
	 */
	@Test
	public void testFromFileNotFound() throws IOException {
		File file = newFile(TEST_KS_FILENAME);
		assertThatThrownBy(() -> RadixKeyStore.fromFile(file, null, false))
			.isInstanceOf(FileNotFoundException.class);
	}

	/**
	 * Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}.
	 */
	@Test
	public void testFromFileReload1() throws IOException, KeyStoreException, PrivateKeyException, PublicKeyException {
		File file = newFile(TEST_KS_FILENAME);
		ECKeyPair kp1 = ECKeyPair.generateNew();
		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
			assertTrue(file.exists());
			ks.writeKeyPair("test", kp1);
		}
		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, false)) {
			ECKeyPair kp2 = ks.readKeyPair("test", false);
			assertArrayEquals(kp1.getPrivateKey(), kp2.getPrivateKey());
			assertArrayEquals(kp1.getPublicKey().getBytes(), kp2.getPublicKey().getBytes());
		}
	}

	/**
	 * Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}.
	 */
	@Test
	public void testFromFileReload2() throws IOException, KeyStoreException, PrivateKeyException, PublicKeyException {
		File file = newFile(TEST_KS_FILENAME);
		final ECKeyPair kp1;
		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
			assertTrue(file.exists());
			kp1 = ks.readKeyPair("test", true);
		}
		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, new char[0], false)) {
			ECKeyPair kp2 = ks.readKeyPair("test", false);
			assertArrayEquals(kp1.getPrivateKey(), kp2.getPrivateKey());
			assertArrayEquals(kp1.getPublicKey().getBytes(), kp2.getPublicKey().getBytes());
		}
	}

	/**
	 * Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}.
	 */
	@Test
	public void testFromFileWrongFilePassword() throws IOException, KeyStoreException {
		File file = newFile(TEST_KS_FILENAME);
		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, "secret1".toCharArray(), true)) {
			assertTrue(file.exists());
			assertTrue(ks.toString().contains(file.toString()));
		}
		assertThatThrownBy(() -> RadixKeyStore.fromFile(file, "secret2".toCharArray(), false))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("wrong password")
			.hasNoCause();
	}

	/**
	 * Test method for {@link RadixKeyStore#readKeyPair(String, boolean)}.
	 */
	@Test
	public void testReadKeyPairFail() throws IOException, KeyStoreException {
		File file = newFile(TEST_KS_FILENAME);
		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
			assertTrue(file.exists());
			assertThatThrownBy(() -> ks.readKeyPair("notexist", false))
				.isInstanceOf(KeyStoreException.class)
				.hasMessageContaining("No such entry")
				.hasNoCause();
		}
	}

	/**
	 * Test method for {@link RadixKeyStore#readKeyPair(String, boolean)}.
	 */
	@Test
	public void testReadKeyPairSuccess() throws IOException, KeyStoreException, PrivateKeyException, PublicKeyException {
		final File file = newFile("keystore.ks");
		final var originalKeypair = ECKeyPair.generateNew();
		final var storePassword = "nopass".toCharArray();
		final var keyPairName = "node";

		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, storePassword, true)) {
			assertTrue(file.exists());

			ks.writeKeyPair(keyPairName, originalKeypair);
		}

		final File renamedFile = new File(TEST_KS_FILENAME);
		file.renameTo(renamedFile);

		try (RadixKeyStore ks = RadixKeyStore.fromFile(renamedFile, storePassword, false)) {
			var loadedKeypair = ks.readKeyPair(keyPairName, false);
			assertThat(loadedKeypair).isEqualTo(originalKeypair);
		}
	}

	/**
	 * Test method for {@link RadixKeyStore#close()}.
	 */
	@Test
	public void testClose() throws IOException, KeyStoreException {
		File file = newFile(TEST_KS_FILENAME);
		@SuppressWarnings("resource")
		RadixKeyStore ks = RadixKeyStore.fromFile(file, "testpassword".toCharArray(), true);
		assertTrue(file.exists());
		ks.close();
		char[] pwd = Whitebox.getInternalState(ks, "storePassword");
		assertEquals(12, pwd.length);
		assertTrue(IntStream.range(0, pwd.length).map(i -> pwd[i]).allMatch(i -> i == ' '));
	}

	/**
	 * Test method for {@link RadixKeyStore#toString()}.
	 */
	@Test
	public void testToString() throws IOException, KeyStoreException {
		File file = newFile(TEST_KS_FILENAME);
		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
			assertThat(ks.toString(), containsString(file.toString()));
			assertThat(ks.toString(), containsString(RadixKeyStore.class.getSimpleName()));
		}
	}

	/**
	 * Test method for ensuring that only secp256k1 keys are allowed.
	 */
	@Test
	public void testInvalidECKey() throws IOException, GeneralSecurityException, KeyStoreException {
		File file = newFile(TEST_KS_FILENAME);

		KeyStore jks = KeyStore.getInstance("pkcs12");
		jks.load(null, TEST_SECRET.toCharArray());

		// Only secp256k1 curve is supported - use a different curve here
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());

        KeyPair keyPair = keyGen.generateKeyPair();
        PrivateKey privKey = keyPair.getPrivate();

        Certificate[] chain = {
        	RadixKeyStore.selfSignedCert(keyPair, 1000, "CN=Invalid")
        };

        KeyStore.PrivateKeyEntry entry = new KeyStore.PrivateKeyEntry(privKey, chain);
        jks.setEntry("test", entry, new KeyStore.PasswordProtection(new char[0]));

        try (RadixKeyStore rks = new RadixKeyStore(file, jks, TEST_SECRET.toCharArray())) {
        	assertThatThrownBy(() -> rks.readKeyPair("test", false))
        		.isInstanceOf(KeyStoreException.class)
        		.hasMessageContaining("Unknown curve")
        		.hasNoCause();
        }
	}

	/**
	 * Test method for ensuring that only PrivateKeyEntry values are allowed.
	 */
	@Test
	public void testInvalidEntry() throws IOException, GeneralSecurityException {
		KeyStore jks = KeyStore.getInstance("pkcs12");
		jks.load(null, "password".toCharArray());

		SecretKey sk = new PKCS12Key(TEST_SECRET.toCharArray());
		KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(sk);

		assertThatThrownBy(() -> jks.setEntry("test", entry, new KeyStore.PasswordProtection(new char[0])))
			.isInstanceOf(java.security.KeyStoreException.class)
			.hasMessageContaining("PKCS12 does not support non-PrivateKeys")
			.hasNoCause();
	}

	private static File newFile(String filename) throws IOException {
		File file = new File(filename);
		if (!Files.deleteIfExists(file.toPath())) {
			// In this case we are fine if "file" does not exist and wasn't deleted.
		}
		assertFalse(file.exists());
		return file;
	}
}
