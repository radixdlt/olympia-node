package com.radixdlt.client.application.identity;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.security.GeneralSecurityException;

public class PrivateKeyEncrypterTest {

	@Test
	public void encryptDecryptPrivateKey() throws GeneralSecurityException, IOException {
		String key = PrivateKeyEncrypter.createEncryptedPrivateKey("Password");
		Reader stringReader = new StringReader(key);
		byte[] decryptedKey = PrivateKeyEncrypter.decryptPrivateKey("Password", stringReader);
		Assert.assertEquals(32, decryptedKey.length);
	}

}