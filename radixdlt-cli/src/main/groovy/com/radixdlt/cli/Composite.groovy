package com.radixdlt.cli

import picocli.CommandLine


class Composite {
    static class EnCrypted {
        @CommandLine.Option(names = ["-k", "--keyfile"], paramLabel = "KEYFILE", description = "location of keyfile.", required = true)
        String keyFile

        @CommandLine.Option(names = ["-p", "--password"], paramLabel = "PASSWORD", description = "password", required = true)
        String password
    }

    static class IdentityInfo {
        @CommandLine.ArgGroup(exclusive = false)
        EnCrypted encrypted

        @CommandLine.Option(names = ["-u", "--unencryptedkeyfile"], paramLabel = "UNENCRYPTED_KEYFILE", description = "location of unencrypted keyfile.")
        String unEncryptedKeyFile
    }
}
