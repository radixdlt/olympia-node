package com.radixdlt.client.core.crypto;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ECPublicKeyTest {

	@Test
	public void encryptionTest() throws MacMismatchException {
		String testPhrase = "Hello World";
		ECKeyPair ecKeyPair = ECKeyPairGenerator.generateKeyPair();
		byte[] encrypted = ecKeyPair.getPublicKey().encrypt(testPhrase.getBytes());
		assertTrue(encrypted.length > 0);
		byte[] decrypted = ecKeyPair.decrypt(encrypted);
		assertEquals(testPhrase, new String(decrypted));
	}
}