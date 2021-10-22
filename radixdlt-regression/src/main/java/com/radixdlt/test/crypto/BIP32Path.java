package com.radixdlt.test.crypto;

import com.google.common.base.Objects;

/**
 * A wrapper around some underlying
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP32 (BIP-32)</a> implementation,
 * that is easily swappable.
 *
 * This class doesn't and shouldn't inherit from said wrapped implementation, but rather use
 * it as a trampoline. Since all interfaces are forwarded to the underlying wrapped implementation
 * this class should really be trivial.
 *
 * However, users are discouraged to construct instances of this class directly, they should
 * rather be using {@link DefaultHDPath}.
 */
final class BIP32Path implements HDPath {

    private final HDPath path;

    private BIP32Path(String path) throws HDPathException {
        this.path = BitcoinJBIP32Path.fromString(path);
    }

    static BIP32Path fromString(String path) throws HDPathException {
        return new BIP32Path(path);
    }

    @Override
    public boolean isHardened() {
        return path.isHardened();
    }

    @Override
    public boolean hasPrivateKey() {
        return path.hasPrivateKey();
    }

    @Override
    public String toString() {
        return path.toString();
    }

    @Override
    public int depth() {
        return path.depth();
    }

    @Override
    public long index() {
        return path.index();
    }

    @Override
    public HDPath next() {
        return path.next();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BIP32Path bip32Path = (BIP32Path) o;
        return Objects.equal(path, bip32Path.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path);
    }
}
