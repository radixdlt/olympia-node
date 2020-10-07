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

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.RadixKeyStore;
import com.radixdlt.crypto.exception.KeyStoreException;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;

import static com.radixdlt.cli.Utils.printfln;
import static com.radixdlt.cli.Utils.println;

/**
 * Create new keypair for use by validator and save created keypair into new or existing keystore.
 * <br>
 * Usage:
 * <pre>
 *  $ radixdlt-cli generate-validator-key [-n=<keypair name>] -k=<keystore name> -p=<keystore password>
 * </pre>
 * Keypair name is optional and if omitted then default value {@code node} is used.
 * <br>
 * <b>WARNING:</b> If keypair is already present in keystore then it is overwritten with new keypair!
 */
@CommandLine.Command(name = "generate-validator-key", mixinStandardHelpOptions = true,
		description = "Generate keypair for Validator")
public class ValidatorKeyGenerator implements Runnable {
	@CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
	private Composite.Encrypted keystoreDetails;

	@Override
	public void run() {
		if (keystoreDetails.keypair() == null || keystoreDetails.keypair().isBlank()) {
			keystoreDetails.keypair("node");
		}

		if (keystoreDetails.isInvalid()) {
			return;
		}

		var keystoreFile = new File(keystoreDetails.keyStore());
		var newFile = !keystoreFile.canWrite();
		var isNew = newFile ? "new" : "existing";
		var keyPair = ECKeyPair.generateNew();
		var publicKey = keyPair.getPublicKey().toBase64();

		printfln("Writing key %s (pubKey: %s) into %s keystore %s",
				keystoreDetails.keypair(), publicKey, isNew, keystoreDetails.keyStore()
		);

		try {
			RadixKeyStore.fromFile(keystoreFile, keystoreDetails.password().toCharArray(), newFile)
					.writeKeyPair(keystoreDetails.keypair(), keyPair);
		} catch (KeyStoreException | IOException e) {
			printfln("Unable to generate keypair due to following error: %s", e.getMessage());
			return;
		}

		println("Done");
	}
}
