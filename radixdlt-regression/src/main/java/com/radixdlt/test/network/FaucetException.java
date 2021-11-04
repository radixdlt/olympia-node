package com.radixdlt.test.network;

/**
 * Thrown when something goes wrong with the faucet
 */
public class FaucetException extends RuntimeException {

    public FaucetException(String message) {
        super(message);
    }

}
