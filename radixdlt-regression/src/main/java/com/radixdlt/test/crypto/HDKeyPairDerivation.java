package com.radixdlt.test.crypto;

import com.radixdlt.test.crypto.errors.HDPathException;

/**
 * A type being able to derive hierarchy deterministic key pairs ({@link HDKeyPair}),
 * from a {@link HDPath}, typically using
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP32 (BIP-32)</a>
 * child key derivation.
 */
public interface HDKeyPairDerivation {

    /**
     * Derives an {@link HDKeyPair} for the given {@link HDPath}
     * @param path used to derive {@link HDKeyPair}
     * @return a derived {@link HDKeyPair} for the given {@link HDPath}
     */
    HDKeyPair deriveKeyAtPath(HDPath path);

    /**
     * Tries to derives an {@link HDKeyPair} for the given {@code path} string, if the string is invalid,
     * then {@link IllegalArgumentException} will be thrown, so use this method with caution and consider
     * using {@link #deriveKeyAtPath(HDPath)} instead.
     * @param path used to derive {@link HDKeyPair}
     * @return a derived {@link HDKeyPair} for the given {@code path} string, if the string is valid path, else
     *  an {@link IllegalArgumentException} will be thrown.
     */
    default HDKeyPair deriveKeyAtPath(String path) {
        try {
            HDPath hdPath = DefaultHDPath.of(path);
            return deriveKeyAtPath(hdPath);
        } catch (HDPathException e) {
            throw new IllegalArgumentException("Failed to construct HD path " + e);
        }
    }
}
