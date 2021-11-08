package com.radixdlt.test.crypto;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.Bytes;


/**
 * A key pair which has been derived using some {@link HDPath}.
 */
public final class HDKeyPair {

    private final ECKeyPair ecKeyPair;
    private final HDPath path;

    // Not public since one should be using `HDKeyPairDerivation.deriveKeyAtPath` instead of using this directly.
    // since we cannot assert that the `HDPath` and the `ECKeyPair` indeed matches.
    HDKeyPair(ECKeyPair ecKeyPair, HDPath path) {
        this.ecKeyPair = ecKeyPair;
        this.path = path;
    }

    /**
     * Returns the {@link HDPath} that was used to derive the Key Pair returned by {@link #keyPair()}.
     * @return the {@link HDPath} that was used to derive the Key Pair returned by {@link #keyPair()}.
     */
    public HDPath path() {
        return path;
    }

    /**
     * The resulting (child) {@link ECKeyPair} after having derived it using the {@link HDPath} and {@link HDKeyPairDerivation}, together with
     * some root key (from a seed).
     * @return resulting (child) {@link ECKeyPair} after having derived it using the {@link HDPath} and {@link HDKeyPairDerivation}, together with
     * 	 * some root key (from a seed).
     */
    public ECKeyPair keyPair() {
        return ecKeyPair;
    }

    @Override
    public String toString() {
        return path.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HDKeyPair hdKeyPair = (HDKeyPair) o;
        return Objects.equal(ecKeyPair, hdKeyPair.ecKeyPair)
            &&
            Objects.equal(path, hdKeyPair.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(ecKeyPair, path);
    }

    @VisibleForTesting
    String privateKeyHex() {
        return Bytes.toHexString(ecKeyPair.getPrivateKey());
    }

    @VisibleForTesting
    String publicKeyHex() {
        return Bytes.toHexString(ecKeyPair.getPublicKey().getCompressedBytes());
    }

    @VisibleForTesting
    boolean isHardened() {
        return path.isHardened();
    }

    @VisibleForTesting
    int depth() {
        return path.depth();
    }

    @VisibleForTesting
    long index() {
        return path.index();
    }
}
