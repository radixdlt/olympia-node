package com.radixdlt.test.system;

import com.radixdlt.test.system.scaffolding.SystemTest;
import org.junit.jupiter.api.Test;

public class SmokeTests extends SystemTest {

    @Test
    public void network_does_not_lose_liveness_when_nodes_restart() {
        runCheck("liveness");
        var firstNode = radixNetwork.getNodes().get(0);
        restartNode(firstNode);
        runCheck("liveness", 60);
        waitForNodeToBeUp(firstNode);
    }

}
