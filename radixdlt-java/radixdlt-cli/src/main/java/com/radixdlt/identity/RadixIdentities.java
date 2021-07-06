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

package com.radixdlt.identity;

import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.security.GeneralSecurityException;

import static com.radixdlt.crypto.ECKeyPair.fromPrivateKey;
import static com.radixdlt.crypto.encryption.PrivateKeyEncrypter.createEncryptedPrivateKey;
import static com.radixdlt.crypto.encryption.PrivateKeyEncrypter.decryptPrivateKey;

/**
 * Radix Identity Helper methods
 */
public final class RadixIdentities {
	private RadixIdentities() {
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
	public static LocalRadixIdentity createNewEncryptedIdentity(Writer writer, String password)
			throws IOException, GeneralSecurityException, PrivateKeyException, PublicKeyException {
		var encryptedKey = createEncryptedPrivateKey(password);

		writer.write(encryptedKey);
		writer.flush();

		var key = fromPrivateKey(decryptPrivateKey(password, new StringReader(encryptedKey)));
		return new LocalRadixIdentity(key);
	}

}
