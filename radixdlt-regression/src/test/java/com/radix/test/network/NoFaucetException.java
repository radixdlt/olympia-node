package com.radix.test.network;

import io.cucumber.java.mk_latn.No;

/**
 * Thrown when a faucet was expected to exist, but didn't exist
 */
public class NoFaucetException extends RuntimeException {

    public NoFaucetException(String message) {
        super(message);
    }
}
