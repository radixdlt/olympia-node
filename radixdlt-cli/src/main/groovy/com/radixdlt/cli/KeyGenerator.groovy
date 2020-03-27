package com.radixdlt.cli

import com.radixdlt.client.application.identity.RadixIdentities
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(name = "generate-key",mixinStandardHelpOptions = true,
        description = "Generate key")
class KeyGenerator implements Runnable {

    @Option(names = ["-p", "--password"], paramLabel = "PASSWORD", description = "password")
    String password

    @Override
    void run() {
        if (password == null) {
            System.err.println("password required")
            System.exit(-1)
        }

        PrintWriter writer = new PrintWriter(System.out)
        RadixIdentities.createNewEncryptedIdentity(writer, password)
        writer.flush()
        writer.close()
    }
}
