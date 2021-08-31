package com.radixdlt.test.system;

import org.junit.jupiter.api.Test;

public class SmokeTesting extends SystemTest {

    @Test
    public void smoke_test_1() {
        runCheck("liveness");

        // stop a node
        //stopNode(0);

        // do another liveness test
    }

}
