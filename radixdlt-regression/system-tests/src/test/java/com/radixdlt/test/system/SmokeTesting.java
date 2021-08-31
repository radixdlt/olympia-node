package com.radixdlt.test.system;

import com.radixdlt.test.system.annotation.RadixSystemTest;

public class SmokeTesting extends SystemTest {

    @RadixSystemTest
    public void smoke_test_1() {
        runCheck("liveness");

        // stop a node
        //stopNode(0);

        // do another liveness test
    }

}
