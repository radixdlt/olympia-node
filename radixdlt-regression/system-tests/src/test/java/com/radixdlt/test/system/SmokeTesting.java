package com.radixdlt.test.system;

import org.junit.jupiter.api.Test;

public class SmokeTesting extends SystemTest {

    @Test
    public void smoke_test_1() {
        runCheck("liveness");
        var firstNode = radixNetwork.getNodes().get(0);
        stopNode(firstNode);
        runCheck("liveness", 60);
        waitForNodeToBeUp(firstNode);
    }

}
