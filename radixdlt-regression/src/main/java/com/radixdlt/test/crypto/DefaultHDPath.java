package com.radixdlt.test.crypto;

import com.radixdlt.test.crypto.errors.HDPathException;

public final class DefaultHDPath {
    private DefaultHDPath() {
        throw new IllegalStateException("Can't construct.");
    }

    /**
     * Tries to create a {@link HDPath} instance from the given {@code path}. If said path is not a valid HDPath,
     * an exception will be thrown.
     * @param path a string representing a hierarchy deterministic path, typically
     *                <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP32 (BIP-32)</a>
     *                compliant.
     * @return a valid hierarchy deterministic path ({@link HDPath}).
     * @throws HDPathException thrown if {@code path} is invalid.
     */
    public static HDPath of(String path) throws HDPathException {
        return BIP32Path.fromString(path);
    }
}

