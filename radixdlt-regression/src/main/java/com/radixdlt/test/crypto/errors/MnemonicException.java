package com.radixdlt.test.crypto.errors;

/**
 * Exception thrown when trying to calculate a seed using some invalid mnemonic.
 */
public class MnemonicException extends Exception {

    public MnemonicException(String errorMessage, Throwable throwable) {
        super(errorMessage, throwable);
    }
}
