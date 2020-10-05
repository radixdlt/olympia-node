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
    public static final String KEYFILE_NAME = System.getenv("RADCLI_ENCRYPTED_KEYFILE")
    public static final String UNENCRYPTED_KEYFILE_NAME = System.getenv("RADCLI_UNENCRYPTED_KEYFILE")
    public static final String KEYFILE_PASSWORD = System.getenv("RADCLI_PWD")
    public static final String KEY_NAME = System.getenv("RADCLI_KEYNAME")

    static RadixIdentity getIdentiyUsingEnvVar() {
        RadixIdentity identity
        if (KEYFILE_NAME != null && KEYFILE_PASSWORD != null && KEY_NAME != null) {
            identity = RadixIdentities.loadOrCreateEncryptedFile(KEYFILE_NAME, KEYFILE_PASSWORD, KEY_NAME)
        } else if (UNENCRYPTED_KEYFILE_NAME != null) {
            identity = RadixIdentities.loadOrCreateFile(UNENCRYPTED_KEYFILE_NAME)
        } else {
            System.err.println("Key required in form of environment variable [RADCLI_ENCRYPTED_KEYFILE & RADCLI_PWD & RADCLI_KEYNAME] or RADCLI_UNENCRYPTED_KEYFILE")
            System.err.println("Run help -h option to check the usage")

            System.exit(-1)
            return
        }
        return identity
    }

    static RadixIdentity getIdentity(String keyFile, String password, String keyName) {
        if (keyFile == null || password == null) {
            System.err.println("Key file name and password are required")
            System.exit(-1)
        }
        return RadixIdentities.loadOrCreateEncryptedFile(keyFile, password, keyName)
    }


    static RadixIdentity getIdentity(IdentityInfo info) {
        if (info?.encrypted != null) {
            return getIdentity(info.encrypted.keyStore, info.encrypted.password, info.encrypted.keypair)
        } else if (info?.unencryptedKeyFile != null) {
            return RadixIdentities.loadOrCreateFile(info.unencryptedKeyFile)
        } else {
            return getIdentiyUsingEnvVar()
        }
    }

    static RadixApplicationAPI getAPI(IdentityInfo identityInfo) {
        return RadixApplicationAPI.create(getRadixNode(), getIdentity(identityInfo))
    }

    static BootstrapByTrustedNode getRadixNode() {
        String bootstrapByTrustedNode = System.getenv(RADIX_BOOTSTRAP_TRUSTED_NODE) ?: {
            println("RADIX_BOOTSTRAP_TRUSTED_NODE env variable not set, using default http://localhost:8080")
            return "http://localhost:8080"
        }()
        println("Using Bootstrap Mechanism: RADIX_BOOTSTRAP_TRUSTED_NODE  ${bootstrapByTrustedNode}")
        RadixNode trustedNode = new RadixNode(new Request.Builder().url(bootstrapByTrustedNode).build())
        return new BootstrapByTrustedNode(trustedNode)
    }
}