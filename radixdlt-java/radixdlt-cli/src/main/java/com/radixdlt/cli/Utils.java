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
package com.radixdlt.cli;

import com.radixdlt.cli.Composite.IdentityInfo;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.BootstrapByTrustedNode;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.crypto.exception.KeyStoreException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import okhttp3.Request;

import java.io.IOException;

public final class Utils {
	private Utils() {
	}

	private static final String KEYFILE_NAME = System.getenv("RADCLI_ENCRYPTED_KEYFILE");
	private static final String UNENCRYPTED_KEYFILE_NAME = System.getenv("RADCLI_UNENCRYPTED_KEYFILE");
	private static final String KEYFILE_PASSWORD = System.getenv("RADCLI_PWD");
	private static final String KEY_NAME = System.getenv("RADCLI_KEYNAME");
	private static final String RADIX_BOOTSTRAP_TRUSTED_NODE = "RADIX_BOOTSTRAP_TRUSTED_NODE";

	public static RadixIdentity getIdentiyUsingEnvVar()
			throws PrivateKeyException, KeyStoreException, PublicKeyException, IOException {
		if (KEYFILE_NAME != null && KEYFILE_PASSWORD != null && KEY_NAME != null) {
			return RadixIdentities.loadOrCreateEncryptedFile(KEYFILE_NAME, KEYFILE_PASSWORD, KEY_NAME);
		} else if (UNENCRYPTED_KEYFILE_NAME != null) {
			return RadixIdentities.loadOrCreateFile(UNENCRYPTED_KEYFILE_NAME);
		}

		println("Key required in form of environment variable ["
				+ "RADCLI_ENCRYPTED_KEYFILE & RADCLI_PWD & RADCLI_KEYNAME] "
				+ "or RADCLI_UNENCRYPTED_KEYFILE"
		);
		println("Run help -h option to check the usage");

		System.exit(-1);
		return null;
	}

	public static RadixIdentity getIdentity(String keyFile, String password, String keyName)
			throws PrivateKeyException, KeyStoreException, PublicKeyException, IOException {
		if (keyFile == null || password == null) {
			println("Key file name and password are required");
			System.exit(-1);
		}
		return RadixIdentities.loadOrCreateEncryptedFile(keyFile, password, keyName);
	}


	public static RadixIdentity getIdentity(IdentityInfo info)
			throws PrivateKeyException, PublicKeyException, IOException, KeyStoreException {
		if (info != null) {
			if (info.encrypted() != null) {
				return getIdentity(info.encrypted().keyStore(), info.encrypted().password(), info.encrypted().keypair());
			} else if (info.unencryptedKeyFile() != null) {
				return RadixIdentities.loadOrCreateFile(info.unencryptedKeyFile());
			}
		}
		return getIdentiyUsingEnvVar();
	}

	public static RadixApplicationAPI getAPI(IdentityInfo identityInfo) {
		try {
			return RadixApplicationAPI.create(getRadixNode(), getIdentity(identityInfo));
		} catch (PrivateKeyException | PublicKeyException | IOException | KeyStoreException e) {
			println("Unable to get access to Radix Application API due to following error:\n" + e.getMessage());
			System.exit(-1);
			// Unreachable
			return null;
		}
	}

	public static BootstrapByTrustedNode getRadixNode() {
		String bootstrapByTrustedNode = System.getenv(RADIX_BOOTSTRAP_TRUSTED_NODE);

		if (bootstrapByTrustedNode == null) {
			println("RADIX_BOOTSTRAP_TRUSTED_NODE env variable not set, using default http://localhost:8080");
			bootstrapByTrustedNode = "http://localhost:8080";
		}
		printfln("Using Bootstrap Mechanism: RADIX_BOOTSTRAP_TRUSTED_NODE %s",  bootstrapByTrustedNode);
		RadixNode trustedNode = new RadixNode(new Request.Builder().url(bootstrapByTrustedNode).build());
		return new BootstrapByTrustedNode(trustedNode);
	}

	public static void println(final String message) {
		System.out.println(message);
	}

	public static void printf(String format, Object... args) {
		System.out.printf(format, args);
	}

	public static void printfln(String format, Object... args) {
		System.out.printf(format, args);
		System.out.println();
	}
}