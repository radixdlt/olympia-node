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

import picocli.CommandLine;

import static com.radixdlt.cli.Utils.println;

public class Composite {
	public static class Encrypted {
		@CommandLine.Option(names = {"-k", "--keystore"}, paramLabel = "KEYSTORE", description = "location of keystore file.", required = true)
		private String keyStore;

		@CommandLine.Option(names = {"-p", "--password"}, paramLabel = "PASSWORD", description = "keystore password", required = true)
		private String password;

		@CommandLine.Option(
				names = {"-n", "--keypair-name"},
				paramLabel = "KEYPAIR_NAME",
				description = "name of keypair to use",
				required = true,
				defaultValue = "node")
		private String keypair;

		boolean isInvalid() {
			if (keyStore == null || keyStore.isBlank()) {
				println("Keystore name must not be empty");
			} else if (password == null || password.isBlank()) {
				System.out.println("Password must not be empty");
			} else if (keypair == null || keypair.isBlank()) {
				System.out.println("Keypair name must not be empty");
			} else {
				return false;
			}
			return true;
		}

		public String keyStore() {
			return keyStore;
		}

		public String password() {
			return password;
		}

		public String keypair() {
			return keypair;
		}

		public void keypair(final String keypair) {
			this.keypair = keypair;
		}
	}

	public static class IdentityInfo {
		@CommandLine.ArgGroup(exclusive = false)
		private Encrypted encrypted;

		@CommandLine.Option(
				names = {"-u", "--unencryptedkeyfile"},
				paramLabel = "UNENCRYPTED_KEYFILE",
				description = "location of unencrypted keyfile."
		)
		private String unencryptedKeyFile;

		public Encrypted encrypted() {
			return encrypted;
		}

		public String unencryptedKeyFile() {
			return unencryptedKeyFile;
		}
	}
}
