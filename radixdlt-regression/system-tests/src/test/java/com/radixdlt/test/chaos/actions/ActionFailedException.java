package com.radixdlt.test.chaos.actions;

public class ActionFailedException extends Error {

    public ActionFailedException(Exception e) {
        super(e);
    }

    public ActionFailedException(String message) {
        super(message);
    }

}
