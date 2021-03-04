package com.radixdlt.test.chaos.actions;

/**
 * Something which affects a testnet
 */
public interface Action {

    void setup();

    void teardown();

}
