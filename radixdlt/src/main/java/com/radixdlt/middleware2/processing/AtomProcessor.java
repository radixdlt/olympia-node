package com.radixdlt.middleware2.processing;

public interface AtomProcessor {
    void start() throws InterruptedException;
    void stop() throws InterruptedException;
}
