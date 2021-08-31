package com.radixdlt.test.system;

import com.radixdlt.test.RadixNetworkTest;
import com.radixdlt.test.network.RadixNode;
import com.radixdlt.test.utils.universe.UniverseVariables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

public class SystemTest extends RadixNetworkTest {

    protected static final Logger logger = LogManager.getLogger();

    private UniverseVariables variables;

    public void restartNode(RadixNode node) {
        radixNetwork.getDockerClient().restartContainer(node.getContainerName());
    }

    @AfterEach
    public void teardown() {
        logger.info("TODO stop nodes");
    }

}
