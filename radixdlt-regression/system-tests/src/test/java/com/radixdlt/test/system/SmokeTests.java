package com.radixdlt.test.system;

import com.radixdlt.test.system.scaffolding.SystemTest;
import org.junit.jupiter.api.Test;

class SmokeTests extends SystemTest {

    @Test
    void network_does_not_lose_liveness_when_nodes_restart() {
        runCheck("liveness");
        var firstNode = radixNetwork.getNodes().get(0);
        restartNode(firstNode);
        runCheck("liveness", 60);
        waitForNodeToBeUp(firstNode);
    }

}
