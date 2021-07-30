package com.radixdlt.test.utils;

/**
 * A runtime exception which is thrown when a cucumber/acceptance test is supposed to fail
 */
public class TestFailureException extends RuntimeException {

    public TestFailureException(String message) {
        super(message);
    }

    public TestFailureException(Exception e) {
        super(e);
    }

}
