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

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.RadixKeyStore;
import com.radixdlt.crypto.exception.CryptoException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Helper methods for key handling.
 */
public final class Keys {
	private Keys() {
		throw new IllegalStateException("Can't construct");
	}

	private static void reset(char[]... passwords) {
		for (var password : passwords) {
			if (password != null) {
				Arrays.fill(password, ' ');
			}
		}
	}

	/**
	 * Read an {@link ECKeyPair} from the specified {@link RadixKeyStore}, using key pair name and environment variables
	 * specific to node. If keystore or key pair don't exists, throws an exception.
	 *
	 * @param keyStore Key store path.
	 */
	public static ECKeyPair readNodeKey(String keyStore) throws IOException, CryptoException {
		return readKey(keyStore, "node", "RADIX_NODE_KEYSTORE_PASSWORD", "RADIX_NODE_KEY_PASSWORD", false);
	}

	/**
	 * Read an {@link ECKeyPair} from the specified {@link RadixKeyStore}, using key pair name and environment variables
	 * specific to stacker. If keystore or key pair don't exists, they are created.
	 *
	 * @param keyStore Key store path.
	 */
	public static ECKeyPair readStakerKey(String keyStore) throws IOException, CryptoException {
		return readKey(keyStore, "wallet", "RADIX_STAKER_KEYSTORE_PASSWORD", "RADIX_STAKER_KEY_PASSWORD", true);
	}

	/**
	 * Read an {@link ECKeyPair} from the specified {@link RadixKeyStore}, using key pair name and environment variables
	 * specific to validator. If keystore or key pair don't exists, they are created.
	 *
	 * @param keyStore Key store path.
	 */
	public static ECKeyPair readValidatorKey(String keyStore) throws IOException, CryptoException {
		return readKey(keyStore, "node", "RADIX_VALIDATOR_KEYSTORE_PASSWORD", "RADIX_VALIDATOR_KEY_PASSWORD", true);
	}

	/**
	 * Read an {@link ECKeyPair} from the specified {@link RadixKeyStore},
	 * using the specified key pair name and environment variables for passwords.
	 * <p>
	 * If the specified key store does not exist, then it will be created,
	 * if possible and {@code create} parameter is set to {@code true}.
	 *
	 * @param keyStorePath The path to the {@link RadixKeyStore}
	 * @param keyName The name of the key within the key store to read
	 * @param keyStorePasswordEnv The environment variable holding the keystore password. This environment variable is read and used
	 * as the password for accessing the key store overall.  If the environment variable does not exist, no password is used.
	 * @param keyPasswordEnv The environment variable holding the key password.  This environment variable is read and used as
	 * the password for accessing the key within the store.  If the environment variable does not exist, no password is used.
	 * @param create If set to {@code true}, then keystore file and keypair will be created if not exists.
	 *
	 * @return The key read from the key store
	 */
	private static ECKeyPair readKey(
		String keyStorePath, String keyName,
		String keyStorePasswordEnv, String keyPasswordEnv,
		boolean create
	) throws IOException, CryptoException {
		var keyPassword = readPassword(keyPasswordEnv);
		var keyStorePassword = readPassword(keyStorePasswordEnv);

		try (var ks = RadixKeyStore.fromFile(new File(keyStorePath), keyStorePassword, create)) {
			return ks.readKeyPair(keyName, create);
		} finally {
			reset(keyPassword, keyStorePassword);
		}
	}

	private static char[] readPassword(String envVar) {
		var envValue = System.getenv(envVar);
		return envValue == null ? null : envValue.toCharArray();
	}
}
