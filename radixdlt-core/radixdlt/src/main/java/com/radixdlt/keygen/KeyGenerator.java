/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.keygen;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.RadixKeyStore;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.io.File;
import java.security.Security;

import static com.radixdlt.utils.functional.Failure.failure;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.fromOptional;

import static java.util.Optional.ofNullable;

/**
 * Command line utility for key generation.
 */
public class KeyGenerator {
	private static final String DEFAULT_KEYPAIR_NAME = "node";

	static {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);
	}

	private final Options options;

	private KeyGenerator() {
		options = new Options()
			.addOption("h", "help", false, "Show usage information (this message)")
			.addOption("k", "keystore", true, "Keystore name")
			.addOption("p", "password", true, "Password for keystore")
			.addOption("n", "keypair-name", true, "Key pair name (optional, default name is 'node')")
			.addOption("pk", "show-public-key", false, "Prints the public key of an existing "
					+ "keypair and exits");
	}

	public static void main(String[] args) {
		new KeyGenerator().run(args);
	}

	private void run(String[] args) {
		parseParameters(args)
			.filter(commandLine -> !commandLine.hasOption("h"), failure(0, ""))
			.filter(commandLine -> commandLine.getOptions().length != 0, failure(0, ""))
			.flatMap(cli -> allOf(parseKeystore(cli), parsePassword(cli), parseKeypair(cli), parseShowPk(cli))
				.flatMap(this::generateKeypair))
			.onFailure(failure -> usage(failure.message()))
			.onSuccessDo(() -> System.out.println("Done"));
	}

	private void usage(String message) {
		if (!message.isEmpty()) {
			System.out.println("ERROR: " + message);
		}
		new HelpFormatter().printHelp(KeyGenerator.class.getSimpleName(), options, true);
	}

	private Result<Void> generateKeypair(String keystore, String password, String keypairName, Boolean shouldShowPk) {
		var keystoreFile = new File(keystore);
		var newFile = !keystoreFile.canWrite();
		var isNew = newFile ? "new" : "existing";

		if (shouldShowPk) {
			return printPublicKey(keystoreFile, password, keypairName, newFile);
		}

		var keyPair = ECKeyPair.generateNew();
		var publicKey = keyPair.getPublicKey().toHex();

		System.out.printf("Writing keypair '%s' [public key: %s]%ninto %s keystore %s%n", keypairName, publicKey, isNew, keystore);

		return Result.wrap(Failure.failure(0, "Error: {0}"), () -> {
			RadixKeyStore.fromFile(keystoreFile, password.toCharArray(), newFile)
				.writeKeyPair(keypairName, keyPair);
			return null;
		});
	}

	private Result<Boolean> parseShowPk(CommandLine commandLine) {
		return Result.ok(commandLine.hasOption("pk"));
	}

	private Result<Void> printPublicKey(File keystoreFile, String password, String keypairName, boolean newFile) {
		if (!keystoreFile.exists() || !keystoreFile.canRead()) {
			return Result.fail(Failure.failure(1, "keystore file '{0}' does not exist or is not accessible",
					keystoreFile));
		}

		return Result.wrap(Failure.failure(0, "Error: {0}"), () -> {
			ECKeyPair keyPair = RadixKeyStore.fromFile(keystoreFile, password.toCharArray(), newFile)
					.readKeyPair(keypairName, false);
			System.out.printf("Public key of keypair '%s': %s%n", keypairName, keyPair.getPublicKey().toHex());
			return null;
		});
	}

	private Result<String> parseKeystore(CommandLine commandLine) {
		return requiredString(commandLine, "k");
	}

	private Result<String> parsePassword(CommandLine commandLine) {
		return requiredString(commandLine, "p");
	}

	private Result<String> parseKeypair(CommandLine commandLine) {
		return requiredString(commandLine, "n").or(Result.ok(DEFAULT_KEYPAIR_NAME));
	}

	private Result<String> requiredString(CommandLine commandLine, String opt) {
		return fromOptional(
			Failure.failure(0, "Parameter -{0} is mandatory", opt), ofNullable(commandLine.getOptionValue(opt))
		);
	}

	private Result<CommandLine> parseParameters(String[] args) {
		return Result.wrap(
			Failure.failure(0, "Error parsing command line parameters: {0}"),
			() -> new DefaultParser().parse(options, args)
		);
	}
}
