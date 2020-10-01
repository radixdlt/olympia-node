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

package com.radixdlt.keys;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.RadixKeyStore;
import com.radixdlt.crypto.exception.KeyStoreException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;

/**
 * Helper methods for key handling.
 */
public final class Keys {

	private Keys() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * Read an {@link ECKeyPair} from the specified {@link RadixKeyStore},
	 * using the specified name and environment variables for passwords.
	 * <p>
	 * If the specified key store does not exist, then it will be created,
	 * if possible.
	 *
	 * @param keyStorePath The path to the {@link RadixKeyStore}
	 * @param keyName The name of the key within the key store to read
	 * @param keyStorePasswordEnv The environment variable holding the key
	 * 		store password.  This environment variable is read and used as
	 * 		the password for accessing the key store overall.  If the
	 * 		environment variable does not exist, no password is used.
	 * @param keyPasswordEnv The environment variable holding the key
	 * 		password.  This environment variable is read and used as
	 * 		the password for accessing the key within the store.  If the
	 * 		environment variable does not exist, no password is used.
	 * @return The key read from the key store
	 * @throws IOException If the key store cannot be accessed or created
	 * @throws CryptoException If the key in the key store is invalid
	 */
	public static ECKeyPair readKey(
		String keyStorePath,
		String keyName,
		String keyStorePasswordEnv,
		String keyPasswordEnv
	) throws IOException, KeyStoreException, PrivateKeyException, PublicKeyException {
		char[] keyPassword = readPassword(keyPasswordEnv);
		char[] keyStorePassword = readPassword(keyStorePasswordEnv);
		try (RadixKeyStore ks = RadixKeyStore.fromFile(new File(keyStorePath), keyStorePassword, true)) {
			return ks.readKeyPair(keyName, true);
		} finally {
			if (keyPassword != null) {
				Arrays.fill(keyPassword, ' ');
			}
			if (keyStorePassword != null) {
				Arrays.fill(keyStorePassword, ' ');
			}
		}
	}

	private static char[] readPassword(String envVar) {
		String envValue = System.getenv(envVar);
		return envValue == null ? null : envValue.toCharArray();
	}
}
