/**
 *
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
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.stream.IntStream;

import javax.crypto.SecretKey;

import org.bouncycastle.jcajce.PKCS12Key;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import com.radixdlt.TestSetupUtils;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.*;

public class RadixKeyStoreTest {

	private static final String TEST_KS_FILENAME = "testfile.ks";

	@BeforeClass
	public static void setup() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	/**
	 * Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}.
	 */
	@Test
	public void testFromFileCreate() throws IOException, CryptoException {
		File file = new File(TEST_KS_FILENAME);
		assertAny(Files.deleteIfExists(file.toPath()));
		assertFalse(file.exists());

		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
			assertTrue(file.exists());
		}
	}

	/**
	 * Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}.
	 */
	@Test
	public void testFromFileNotFound() throws IOException {
		File file = new File(TEST_KS_FILENAME);
		assertAny(Files.deleteIfExists(file.toPath()));
		assertFalse(file.exists());

		assertThatThrownBy(() -> RadixKeyStore.fromFile(file, null, false))
			.isInstanceOf(FileNotFoundException.class);
	}

	/**
	 * Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}.
	 */
	@Test
	public void testFromFileReload1() throws IOException, CryptoException {
		File file = new File(TEST_KS_FILENAME);
		assertAny(Files.deleteIfExists(file.toPath()));
		assertFalse(file.exists());

		ECKeyPair kp1 = new ECKeyPair();
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
	public void testFromFileReload2() throws IOException, CryptoException {
		File file = new File(TEST_KS_FILENAME);
		assertAny(Files.deleteIfExists(file.toPath()));
		assertFalse(file.exists());

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
	public void testFromFileWrongFilePassword() throws IOException, CryptoException {
		File file = new File(TEST_KS_FILENAME);
		assertAny(Files.deleteIfExists(file.toPath()));
		assertFalse(file.exists());

		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, "secret1".toCharArray(), true)) {
			assertTrue(file.exists());
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
	public void testReadKeyPairFail() throws IOException, CryptoException {
		File file = new File(TEST_KS_FILENAME);
		assertAny(Files.deleteIfExists(file.toPath()));
		assertFalse(file.exists());

		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
			assertTrue(file.exists());
			assertThatThrownBy(() -> ks.readKeyPair("notexist", false))
				.isInstanceOf(CryptoException.class)
				.hasMessageContaining("No such entry")
				.hasNoCause();
		}
	}

	/**
	 * Test method for {@link RadixKeyStore#close()}.
	 */
	@Test
	public void testClose() throws IOException, CryptoException {
		File file = new File(TEST_KS_FILENAME);
		assertAny(Files.deleteIfExists(file.toPath()));
		assertFalse(file.exists());

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
	public void testToString() throws IOException, CryptoException {
		File file = new File(TEST_KS_FILENAME);
		assertAny(Files.deleteIfExists(file.toPath()));
		assertFalse(file.exists());

		try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
			assertThat(ks.toString(), containsString(file.toString()));
			assertThat(ks.toString(), containsString(RadixKeyStore.class.getSimpleName()));
		}
	}

	/**
	 * Test method for ensuring that only secp256k1 keys are allowed.
	 */
	@Test
	public void testInvalidECKey() throws IOException, GeneralSecurityException, CryptoException {
		File file = new File(TEST_KS_FILENAME);
		assertAny(Files.deleteIfExists(file.toPath()));
		assertFalse(file.exists());

		KeyStore jks = KeyStore.getInstance("pkcs12");
		jks.load(null, "password".toCharArray());

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());

        KeyPair keyPair = keyGen.generateKeyPair();
        PrivateKey privKey = keyPair.getPrivate();

        Certificate[] chain = {
        	RadixKeyStore.selfSignedCert(keyPair, 1000, "CN=Invalid")
        };

        KeyStore.PrivateKeyEntry entry = new KeyStore.PrivateKeyEntry(privKey, chain);
        jks.setEntry("test", entry, new KeyStore.PasswordProtection(new char[0]));

        try (RadixKeyStore rks = new RadixKeyStore(file, jks, "password".toCharArray())) {
        	assertThatThrownBy(() -> rks.readKeyPair("test", false))
        		.isInstanceOf(CryptoException.class)
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

		SecretKey sk = new PKCS12Key("secret".toCharArray());
		KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(sk);

		assertThatThrownBy(() -> jks.setEntry("test", entry, new KeyStore.PasswordProtection(new char[0])))
			.isInstanceOf(KeyStoreException.class)
			.hasMessageContaining("PKCS12 does not support non-PrivateKeys")
			.hasNoCause();
	}

	private static void assertAny(boolean result) {
		if (result) {
			// Nothing to do
		}
	}
}
