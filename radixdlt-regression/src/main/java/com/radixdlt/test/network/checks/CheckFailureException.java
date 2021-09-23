package com.radixdlt.test.network.checks;

public class CheckFailureException extends Error {

    public CheckFailureException(Check check) {
        super(check.getClass().getSimpleName());
    }

    public CheckFailureException(String text) {
        super(text);
    }

}

