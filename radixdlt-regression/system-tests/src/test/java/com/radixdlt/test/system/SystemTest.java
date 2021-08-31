package com.radixdlt.test.system;

import com.radixdlt.test.RadixNetworkTest;
import com.radixdlt.test.network.RadixNode;
import com.radixdlt.test.utils.TestingUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;

public class SystemTest extends RadixNetworkTest {

    protected static final Logger logger = LogManager.getLogger();

    public void restartNode(RadixNode node) {
        radixNetwork.getDockerClient().restartContainer(node.getContainerName());
        logger.info("Restarted (docker restart) node {}", node);
    }

    public void waitForNodeToBeUp(RadixNode node) {
        TestingUtils.waitForNodeToBeUp(node, radixNetwork.getConfiguration());
        logger.info("Node {} is UP", node);
    }

    @AfterEach
    public void teardown() {
        if (Boolean.parseBoolean(System.getenv("RADIXDLT_DOCKER_DO_NOT_CREATE_NETWORK"))) {
            return;
        }
        String networkName = radixNetwork.getConfiguration().getDockerConfiguration().getNetworkName();
        getNetwork().getDockerClient().wipeNetwork(networkName);
        logger.info("Wiped network {} and removed its nodes", networkName);
    }

}
