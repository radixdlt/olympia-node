package com.radixdlt.cli

import com.radixdlt.cli.Composite.IdentityInfo
import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.application.identity.RadixIdentities
import com.radixdlt.client.application.identity.RadixIdentity
import com.radixdlt.client.core.BootstrapByTrustedNode
import com.radixdlt.client.core.network.RadixNode
import okhttp3.Request

class Utils {
    static String RADIX_BOOTSTRAP_TRUSTED_NODE = "RADIX_BOOTSTRAP_TRUSTED_NODE"

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

    static RadixIdentity getIdentity(IdentityInfo info) {
        if (info.encrypted != null) {
            return getIdentity(info.encrypted.keyFile, info.encrypted.password, null)
        } else if (info.unEncryptedKeyFile != null) {
            return getIdentity(null, null, info.unEncryptedKeyFile)
        }
        return null
    }

    static RadixApplicationAPI getAPI(IdentityInfo identityInfo) {
        return RadixApplicationAPI.create(getRadixNode(), getIdentity(identityInfo))
    }

    static BootstrapByTrustedNode getRadixNode() {
        String bootstrapByTrustedNode = System.getenv(RADIX_BOOTSTRAP_TRUSTED_NODE) ?: {
            println("RADIX_BOOTSTRAP_TRUSTED_NODE  env variable not set, using default http://localhost:8080")
            return "http://localhost:8080"
        }()
        println("Using Bootstrap Mechanism: RADIX_BOOTSTRAP_TRUSTED_NODE  ${bootstrapByTrustedNode}");
        RadixNode trustedNode = new RadixNode(new Request.Builder().url(bootstrapByTrustedNode).build());
        return new BootstrapByTrustedNode(trustedNode);
    }
}