package com.radixdlt.test.crypto.errors;

/**
 * Exception thrown when trying to create an instance of {@link HDPath} from some invalid string.
 */
public class HDPathException extends Exception {

    public HDPathException(String errorMessage) {
        super(errorMessage);
    }
}
