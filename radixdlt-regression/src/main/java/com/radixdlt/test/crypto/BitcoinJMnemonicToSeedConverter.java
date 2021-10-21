package com.radixdlt.test.crypto;

import com.radixdlt.test.crypto.errors.MnemonicException;
import org.bitcoinj.crypto.MnemonicCode;

import java.util.List;
import java.util.Objects;

import static org.bitcoinj.core.Utils.WHITESPACE_SPLITTER;

public final class BitcoinJMnemonicToSeedConverter {

    private BitcoinJMnemonicToSeedConverter() {
        throw new IllegalStateException("Can't construct.");
    }

    public static byte[] seedFromMnemonicAndPassphrase(List<String> words, String passphrase) throws MnemonicException {
        validateMnemonic(words);
        Objects.requireNonNull(passphrase);
        return MnemonicCode.toSeed(words, passphrase);
    }

    public static byte[] seedFromMnemonic(List<String> words) throws MnemonicException {
        return seedFromMnemonicAndPassphrase(words, HDPaths.BIP39_MNEMONIC_NO_PASSPHRASE);
    }

    static byte[] seedFromMnemonicStringAndPassphrase(String mnemonic, String passphrase) throws MnemonicException {
        return seedFromMnemonicAndPassphrase(wordsFromMnemonicString(mnemonic), passphrase);
    }

    static byte[] seedFromMnemonicString(String mnemonic) throws MnemonicException {
        return seedFromMnemonicStringAndPassphrase(mnemonic, HDPaths.BIP39_MNEMONIC_NO_PASSPHRASE);
    }

    static void validateMnemonic(List<String> words) throws MnemonicException {
        try {
            MnemonicCode.INSTANCE.check(words);
        } catch (org.bitcoinj.crypto.MnemonicException e) {
            throw new MnemonicException("Mnemonic does not pass validation check", e);
        }
    }

    static void validateMnemonicString(String mnemonic) throws MnemonicException {
        validateMnemonic(wordsFromMnemonicString(mnemonic));
    }

    static boolean isValidMnemonic(List<String> words) {
        try {
            validateMnemonic(words);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isValidMnemonicString(String mnemonic) {
        return isValidMnemonic(wordsFromMnemonicString(mnemonic));
    }

    private static List<String> wordsFromMnemonicString(String string) {
        return WHITESPACE_SPLITTER.splitToList(string);
    }

    static byte[] seedFromEntropyAndPassphrase(byte[] entropy, String passphrase) throws MnemonicException {
        List<String> mnemonicFromEntropy = null;
        try {
            mnemonicFromEntropy = MnemonicCode.INSTANCE.toMnemonic(entropy);
        } catch (org.bitcoinj.crypto.MnemonicException.MnemonicLengthException e) {
            throw new MnemonicException("Failed to created seed from entropy, incorrect length", e);
        }
        return seedFromMnemonicAndPassphrase(mnemonicFromEntropy, passphrase);
    }

    static byte[] seedFromEntropy(byte[] entropy) throws MnemonicException {
        return seedFromEntropyAndPassphrase(entropy, HDPaths.BIP39_MNEMONIC_NO_PASSPHRASE);
    }

}
