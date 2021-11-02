package com.radixdlt.test.crypto;

import com.radixdlt.test.crypto.errors.MnemonicException;
import java.util.List;

/**
 * Converter of a <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic code,
 * to a seed (byte array) used to create some (HD root) key pair.
 */
public final class DefaultMnemonicToSeedConverter {
    private  DefaultMnemonicToSeedConverter() {
        throw new IllegalStateException("Can't construct.");
    }

    /**
     * Returns a binary seed from a list of BIP39 mnemonic words + provided passphrase
     * @param words list of BIP39 mnemonic words.
     * @param passphrase BIP39 seed passphrase, can be empty, but must not be null.
     * @return a binary seed from a list of BIP39 mnemonic words + provided passphrase
     * @throws MnemonicException thrown if the mnemonic {@code words} is invalid.
     */
    public static byte[] seedFromMnemonicAndPassphrase(List<String> words, String passphrase) throws MnemonicException {
        return BitcoinJMnemonicToSeedConverter.seedFromMnemonicAndPassphrase(words, passphrase);
    }

    /**
     * Returns a binary seed from a list of BIP39 mnemonic words, using an empty passphrase ({@code ""})
     * @param words list of BIP39 mnemonic words.
     * @return a binary seed from a list of BIP39 mnemonic words, using an empty passphrase ({@code ""})
     * @throws MnemonicException thrown if the mnemonic {@code words} is invalid.
     */
    public static byte[] seedFromMnemonic(List<String> words) throws MnemonicException {
        return BitcoinJMnemonicToSeedConverter.seedFromMnemonic(words);
    }

    /**
     * Returns a binary seed from a BIP39 mnemonic string (assuming a space is used to separate the words) + provided passphrase
     * @param mnemonic a BIP39 mnemonic string (assuming a space is used to separate the words)
     * @param passphrase BIP39 seed passphrase, can be empty, but must not be null.
     * @return a binary seed from a list of BIP39 mnemonic words + provided passphrase
     * @throws MnemonicException thrown if the {@code mnemonic} is invalid.
     */
    public static byte[] seedFromMnemonicStringAndPassphrase(String mnemonic, String passphrase) throws MnemonicException {
        return BitcoinJMnemonicToSeedConverter.seedFromMnemonicStringAndPassphrase(mnemonic, passphrase);
    }

    /**
     * Returns a binary seed from a BIP39 mnemonic string (assuming a space is used to separate the words), using an empty passphrase ({@code ""})
     * @param mnemonic a BIP39 mnemonic string (assuming a space is used to separate the words)
     * @return a binary seed from a list of BIP39 mnemonic words, using an empty passphrase ({@code ""})
     * @throws MnemonicException thrown if the {@code mnemonic} is invalid.
     */
    public static byte[] seedFromMnemonicString(String mnemonic) throws MnemonicException {
        return BitcoinJMnemonicToSeedConverter.seedFromMnemonicString(mnemonic);
    }

    /**
     * Validates the list of words to see if they form a valid BIP39 mnemonic, if the words are invalid an exception is thrown.
     * @param words a list of words to check if they form a valid BIP39 mnemonic.
     * @throws MnemonicException thrown if the mnemonic {@code words} is invalid.
     */
    public static void validateMnemonic(List<String> words) throws MnemonicException {
        BitcoinJMnemonicToSeedConverter.validateMnemonic(words);
    }

    /**
     * Return {@code true} iff the mnemonic {@code words} form a valid BIP39 mnemonic, else {@code false } is returned.
     * @param words a list of words to check if they form a valid BIP39 mnemonic.
     */
    public static boolean isValidMnemonic(List<String> words) {
        return BitcoinJMnemonicToSeedConverter.isValidMnemonic(words);
    }

    /**
     * Validates the {@code mnemonic} string (assuming a space is used to separate the words),
     * form a valid BIP39 mnemonic, if the words are invalid an exception is thrown.
     * @param mnemonic a BIP39 mnemonic string (assuming a space is used to separate the words)
     * @throws MnemonicException thrown if the {@code mnemonic} is invalid.
     */
    public static void validateMnemonicString(String mnemonic) throws MnemonicException {
        BitcoinJMnemonicToSeedConverter.validateMnemonicString(mnemonic);
    }

    /**
     * Return {@code true} iff the {@code mnemonic} string (assuming a space is used to separate the words)
     * form a valid BIP39 mnemonic, else {@code false } is returned.
     * @param mnemonic a BIP39 mnemonic string (assuming a space is used to separate the words)
     */
    public static boolean isValidMnemonicString(String mnemonic) {
        return BitcoinJMnemonicToSeedConverter.isValidMnemonicString(mnemonic);
    }
}