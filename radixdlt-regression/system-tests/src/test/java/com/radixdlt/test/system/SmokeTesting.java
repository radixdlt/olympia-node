package com.radixdlt.test.system;

import org.junit.jupiter.api.Test;

public class SmokeTesting extends SystemTest {

    //@Test
    public void smoke_test_1() {
        runCheck("liveness");
        var firstNode = radixNetwork.getNodes().get(0);
        restartNode(firstNode);
        runCheck("liveness", 60);
        waitForNodeToBeUp(firstNode);
    }

    @Test
    public void one() {
        logger.info("\none!\n");

    }

    @Test
    public void two() {
        logger.info("\ntwo!\n");
    }

}
