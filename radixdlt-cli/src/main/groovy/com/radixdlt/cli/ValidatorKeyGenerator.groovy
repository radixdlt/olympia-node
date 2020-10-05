package com.radixdlt.cli

import com.radixdlt.crypto.ECKeyPair
import com.radixdlt.crypto.RadixKeyStore
import picocli.CommandLine

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
class ValidatorKeyGenerator implements Runnable {
	@CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
	Composite.Encrypted keystoreDetails

	@Override
	void run() {
		if (keystoreDetails.isInvalid()) {
			return
		}

		def keystoreFile = new File(keystoreDetails.keyStore)
		def newFile = !keystoreFile.canWrite()
		def isNew = newFile ? "new" : "existing"
		def keyPair = ECKeyPair.generateNew()
		def publicKey = keyPair.publicKey.toBase64()

		println "Writing key ${keystoreDetails.keypair} (pubKey: ${publicKey}) into ${isNew} keystore ${keystoreDetails.keyStore}"

		RadixKeyStore.fromFile(keystoreFile, keystoreDetails.password.getChars(), newFile)
				.withCloseable((RadixKeyStore keyStore) ->
						keyStore.writeKeyPair(keystoreDetails.keypair, keyPair));
		println "Done"
	}
}
