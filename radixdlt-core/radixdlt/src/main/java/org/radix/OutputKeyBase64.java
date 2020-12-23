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

package org.radix;

import java.io.IOException;
import java.security.Security;
import java.util.Base64;

import com.radixdlt.crypto.exception.KeyStoreException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.keys.Keys;

/**
 * Utility application that reads a private key from a specified keystore
 * and prints out the key, base 64 encoded.  Used for setting up faucets.
 */
public final class OutputKeyBase64 {

	/**
	 * Print out the private keys from the provided files.
	 *
	 * @param args An array of file names.  If empty or null, the default "universe.ks" is used.
	 * @throws IOException if there was a problem reading any of the files
	 * @throws KeyStoreException if any of the key store files is invalid
	 * @throws PublicKeyException if any of the public keys in store is invalid
	 * @throws PrivateKeyException if any of the private keys in store is invalid
	 */
	public static void main(String[] args)
			throws IOException, PublicKeyException, PrivateKeyException, KeyStoreException {
		final String[] files = (args == null || args.length == 0) ? new String[] { "universe.ks" } : args;

		Security.insertProviderAt(new BouncyCastleProvider(), 1);

		for (String file : files) {
			ECKeyPair key = Keys.readKey(file, file, "RADIX_KEYSTORE_PASSWORD", "RADIX_KEY_PASSWORD");
			System.out.format("%s: %s%n", file, Base64.getEncoder().encodeToString(key.getPrivateKey()));
		}
	}
}
