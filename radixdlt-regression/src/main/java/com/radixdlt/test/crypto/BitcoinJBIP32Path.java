package com.radixdlt.test.crypto;

import com.google.common.base.Objects;
import com.radixdlt.test.crypto.errors.HDPathException;
import org.bitcoinj.crypto.ChildNumber;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A BIP32 path wrapping underlying implementation using BitcoinJ.
 */
public final class BitcoinJBIP32Path implements HDPath {

    private static final String BIP32_HARDENED_MARKER_BITCOINJ = "H";

    private final org.bitcoinj.crypto.HDPath path;

    private BitcoinJBIP32Path(org.bitcoinj.crypto.HDPath path) {
        this.path = path;
    }

    static BitcoinJBIP32Path fromPath(HDPath path) {
        try {
            return fromString(path.toString());
        } catch (HDPathException e) {
            throw new IllegalStateException("String representation of any path should be correct.", e);
        }
    }

    static BitcoinJBIP32Path fromString(String path) throws HDPathException {
        HDPaths.validateHDPathString(path);
        return new BitcoinJBIP32Path(org.bitcoinj.crypto.HDPath.parsePath(toBitcoinJPath(path)));
    }

    private static String toBitcoinJPath(String standardPath) {
        // For some reason BitcoinJ chose to not use standard notation of hardened path components....
        return standardPath.replace(HDPaths.BIP32_HARDENED_MARKER_STANDARD, BIP32_HARDENED_MARKER_BITCOINJ);
    }

    private static String standardizePath(String nonStandardPath) {
        // For some reason BitcoinJ chose to not use standard notation of hardened path components....
        return nonStandardPath.replace(BIP32_HARDENED_MARKER_BITCOINJ, HDPaths.BIP32_HARDENED_MARKER_STANDARD);
    }

    private int indexOfLastComponent() {
        if (depth() == 0) {
            throw new IllegalStateException("Trying to access component of a BIP32 path with 0 depth, this is undefined.");
        }
        return depth() - 1;
    }

    private ChildNumber lastComponent() {
        return path.get(indexOfLastComponent());
    }

    List<ChildNumber> componentsUpTo(int index) {
        return IntStream.range(0, index).mapToObj(path::get).collect(Collectors.toList());
    }

    List<ChildNumber> components() {
        return componentsUpTo(depth());
    }

    @Override
    public boolean isHardened() {
        return lastComponent().isHardened();
    }

    @Override
    public boolean hasPrivateKey() {
        return path.hasPrivateKey();
    }

    @Override
    public String toString() {
        return standardizePath(path.toString());
    }

    @Override
    public int depth() {
        return path.size();
    }

    @Override
    public long index() {
        long index = lastComponent().num();
        if (!isHardened()) {
            return index;
        }
        index += HDPaths.BIP32_HARDENED_VALUE_INCREMENT;
        return index;
    }

    @Override
    public HDPath next() {
        ArrayList<ChildNumber> nextPathComponents = new ArrayList<>(pathListFromBIP32Path(this, indexOfLastComponent()));
        nextPathComponents.add(new ChildNumber(lastComponent().num() + 1, lastComponent().isHardened()));
        org.bitcoinj.crypto.HDPath nextPath = new org.bitcoinj.crypto.HDPath(this.hasPrivateKey(), nextPathComponents);
        return new BitcoinJBIP32Path(nextPath);
    }

    private static List<ChildNumber> pathListFromBIP32Path(BitcoinJBIP32Path path, @Nullable Integer toIndex) {
        return path.componentsUpTo(toIndex == null ? path.indexOfLastComponent() : toIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BitcoinJBIP32Path that = (BitcoinJBIP32Path) o;
        return Objects.equal(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path);
    }
}
