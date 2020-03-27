package com.radixdlt.cli

import com.radixdlt.client.application.identity.RadixIdentities
import com.radixdlt.client.application.identity.RadixIdentity

class Utils {
    static RadixIdentity getIdentity(String keyFile, String password, String unEncryptedKeyFile) {
        RadixIdentity identity

        if (keyFile != null) {
            if (password == null) {
                System.err.println("password required")
                System.exit(-1)
                return
            }
            identity = RadixIdentities.loadOrCreateEncryptedFile(keyFile, password)
        } else if (unEncryptedKeyFile != null) {
            identity = RadixIdentities.loadOrCreateFile(unEncryptedKeyFile)
        } else if (System.getenv("RADCLI_ENCRYPTED_KEYFILE") != null && System.getenv("RADCLI_PWD") != null) {
            identity = RadixIdentities.loadOrCreateEncryptedFile(System.getenv("RADCLI_ENCRYPTED_KEYFILE"), System.getenv("RADCLI_PWD"))
        } else if (System.getenv("RADCLI_UNENCRYPTED_KEYFILE") != null) {
            identity = RadixIdentities.loadOrCreateFile(System.getenv("RADCLI_UNENCRYPTED_KEYFILE"))
        } else {
            System.err.println("key required")
            System.exit(-1)
            return
        }
        return identity
    }

    static RadixIdentity getIdentity(Composite.IdentityInfo info) {
        if (info.encryptedFileInfo != null) {
            return getIdentity(info.encryptedFileInfo.keyFile, info.encryptedFileInfo.password, null)
        } else if (info.unEncryptedKeyFile != null) {
            return getIdentity(null, null, info.unEncryptedKeyFile)
        }
        return null
    }


}
