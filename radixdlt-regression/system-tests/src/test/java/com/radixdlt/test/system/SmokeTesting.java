package com.radixdlt.test.system;

import com.radixdlt.test.system.annotation.RadixSystemTest;

public class SmokeTesting extends SystemTest {

    @RadixSystemTest
    public void smoke_test_1() {
        logger.info("Inside test case 1!!");

        // do a liveness test
        //LivenessCheck a;

        // stop a node
        //stopNode(0);

        // do another liveness test
    }

}
