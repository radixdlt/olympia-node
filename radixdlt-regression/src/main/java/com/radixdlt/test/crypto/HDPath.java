package com.radixdlt.test.crypto;

/**
 * A path use to deterministically derive a key pair in some hierarchy given by some root key. The path is
 * typically <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP32 (BIP-32)</a> compliant.
 */
public interface HDPath {

    /**
     * The string representation of the BIP32 path, using standard notation "'" for hardened components, e.g.
     * "m/44'/536'/2'/1/4
     * @return a string representation of the BIP32 path, using standard notation "'" for hardened components, e.g.
     * "m/44'/536'/2'/1/4
     */
    @Override
    String toString();

    /**
     * Is this a path to a private key?
     *
     * @return true if yes, false if no or a partial path
     */
    boolean hasPrivateKey();

    /**
     * Whether the last component in the path is "hardened" or not, if the last component is not hardened, it does not mean
     * that potentially earlier components are not hardened as well, i.e. this only looks at the <b>last</b> component.
     * @return whether the last component in the path is "hardened" or not, if the last component is not hardened, it does not mean
     * that potentially earlier components are not hardened as well, i.e. this only looks at the <b>last</b> component.
     */
    boolean isHardened();

    /**
     * The number of components in the path, `1` is the lowest possible value, and most commonly 5 is the max depth, even though BIP32
     * supports a longer depth. The depth of "m/0" is 1, the depth of "m/0'/1" is 2 etc.
     * @return number of components in the path, `1` is the lowest possible value, and most commonly 5 is the max depth, even though BIP32
     * supports a longer depth. The depth of "m/0" is 1, the depth of "m/0'/1" is 2 etc.
     */
    int depth();

    /**
     * Returns the value of the last component, taking into account if it is hardened or not, i.e. the index of the path "m/0/0" is 0, but
     * the index of the path "m/0/0'" - which is hardened - is 2147483648
     * (0 | {@link HDPaths#BIP32_HARDENED_VALUE_INCREMENT HARDENED_BITMASK}) -
     * and the index of "m/0/1'" is 2147483649
     * (1 | {@link HDPaths#BIP32_HARDENED_VALUE_INCREMENT HARDENED_BITMASK}).
     *
     * @return the value of the last component, taking into account if it is hardened or not, i.e. the index of the path "m/0/0" is 0, but
     * the index of the path "m/0/0'" - which is hardened - is 2147483648
     * (0 | {@link HDPaths#BIP32_HARDENED_VALUE_INCREMENT HARDENED_BITMASK}) -
     * and the index of "m/0/1'" is 2147483649
     * (1 | {@link HDPaths#BIP32_HARDENED_VALUE_INCREMENT HARDENED_BITMASK}).
     */
    long index();


    /**
     * Returns the path to the subsequent child key key pair, e.g. identical to this path but with the value of {@link #index() + 1}.
     * @return the path to the subsequent child key key pair, e.g. identical to this path but with the value of {@link #index()} + 1.
     */
    HDPath next();
}
