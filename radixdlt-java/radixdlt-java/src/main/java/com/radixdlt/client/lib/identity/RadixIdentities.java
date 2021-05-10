/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.lib.identity;

import com.radixdlt.crypto.ECKeyPair;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.security.GeneralSecurityException;

import com.radixdlt.crypto.RadixKeyStore;
import com.radixdlt.crypto.encryption.PrivateKeyEncrypter;
import com.radixdlt.crypto.exception.KeyStoreException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import org.bouncycastle.util.encoders.Base64;

/**
 * Radix Identity Helper methods
 */
public class RadixIdentities {
	private RadixIdentities() {
	}

	/**
	 * Creates a radix identity from a private key
	 * @param privateKeyBase64 the private key encoded in base 64
	 * @return a radix identity
	 * @throws PrivateKeyException in case of issues with private key used to create identity
	 * @throws PublicKeyException in case of issues with preparing public key
	 */
	public static RadixIdentity fromPrivateKeyBase64(String privateKeyBase64)
			throws PrivateKeyException, PublicKeyException {
		final ECKeyPair myKey = ECKeyPair.fromPrivateKey(Base64.decode(privateKeyBase64));
		return new LocalRadixIdentity(myKey);
	}

	/**
	 * Creates a new radix identity which is not stored anywhere
	 * @return an unstored radix identity
	 */
	public static LocalRadixIdentity createNew() {
		return new LocalRadixIdentity(ECKeyPair.generateNew());
	}

	/**
	 * Loads or creates an unencrypted file containing a private key and returns
	 * the associated radix identity
	 * @param keyFile the file to load or create
	 * @return a radix identity
	 * @throws IOException in case of I/O errors
	 * @throws PrivateKeyException in case of issues with private key used to create identity
	 * @throws PublicKeyException in case of issues with preparing public key
	 */
	public static RadixIdentity loadOrCreateFile(File keyFile)
			throws IOException, PrivateKeyException, PublicKeyException {
		final ECKeyPair ecKeyPair;
		if (keyFile.exists()) {
			ecKeyPair = ECKeyPair.fromFile(keyFile);
		} else {
			ecKeyPair = ECKeyPair.generateNew();

			try (FileOutputStream io = new FileOutputStream(keyFile)) {
				io.write(ecKeyPair.getPrivateKey());
			}
		}

		return new LocalRadixIdentity(ecKeyPair);
	}

	/**
	 * Loads or creates an unencrypted file containing a private key and returns
	 * the associated radix identity
	 * @param filePath the path of the file to load or create
	 * @return a radix identity
	 * @throws IOException in case of I/O errors
	 * @throws PrivateKeyException in case of issues with private key used to create identity
	 * @throws PublicKeyException in case of issues with preparing public key
	 */
	public static RadixIdentity loadOrCreateFile(String filePath)
			throws IOException, PrivateKeyException, PublicKeyException {
		return loadOrCreateFile(new File(filePath));
	}

	/**
	 * Loads or creates an encrypted file containing a private key and returns
	 * the associated radix identity
	 * @param filePath the path of the file to load or create
	 * @param password the password to decrypt the encrypted file
	 * @param keyName name of keypair to use from key store
	 * @return a radix identity
	 * @throws IOException in case of I/O errors
	 * @throws PrivateKeyException in case of issues with private key used to create identity
	 * @throws PublicKeyException in case of issues with preparing public key
	 * @throws KeyStoreException in case of issues accessing keystore and retrieving keypair
	 */
	public static RadixIdentity loadOrCreateEncryptedFile(String filePath, String password, String keyName)
			throws IOException, PrivateKeyException, PublicKeyException, KeyStoreException {
		var keyFile = new File(filePath);
		var keyStore = RadixKeyStore.fromFile(keyFile, password.toCharArray(), !keyFile.exists());
		var keyPair = keyStore.readKeyPair(keyName, !keyFile.exists());

		return new LocalRadixIdentity(keyPair);
	}

	/**
	 * Creates a new private key and encrypts it and then writes/flushes the result to a given writer
	 *
	 * @param writer the writer to write the encrypted private key to
	 * @param password the password to encrypt the private key with
	 * @return the radix identity created
	 * @throws IOException in case of I/O errors
	 * @throws GeneralSecurityException in case of issues during encryption of private key
	 * @throws PrivateKeyException in case of issues with private key used to create identity
	 * @throws PublicKeyException in case of issues with preparing public key
	 */
	public static RadixIdentity createNewEncryptedIdentity(Writer writer, String password)
			throws IOException, GeneralSecurityException, PrivateKeyException, PublicKeyException {
		String encryptedKey = PrivateKeyEncrypter.createEncryptedPrivateKey(password);
		writer.write(encryptedKey);
		writer.flush();
		return readEncryptedIdentity(new StringReader(encryptedKey), password);
	}

	/**
	 * Reads an encrypted private key from a given reader and decrypts it with a given password
	 * @param reader the reader to read the encrypted private key from
	 * @param password the password to decrypt the private key with
	 * @return the decrypted radix identity
	 * @throws IOException in case of I/O errors
	 * @throws GeneralSecurityException in case of issues during decryption of private key
	 * @throws PrivateKeyException in case of issues with private key used to create identity
	 * @throws PublicKeyException in case of issues with preparing public key
	 */
	public static RadixIdentity readEncryptedIdentity(Reader reader, String password)
			throws IOException, GeneralSecurityException, PrivateKeyException, PublicKeyException {
		final ECKeyPair key = ECKeyPair.fromPrivateKey(PrivateKeyEncrypter.decryptPrivateKey(password, reader));
		return new LocalRadixIdentity(key);
	}
}
