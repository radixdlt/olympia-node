package com.radixdlt.test.system.harness;

import com.radixdlt.test.RadixNetworkTest;
import com.radixdlt.test.network.RadixNode;
import com.radixdlt.test.utils.TestingUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;

public class SystemTest extends RadixNetworkTest {

    protected static final Logger logger = LogManager.getLogger();

    public void restartNode(RadixNode node) {
        radixNetwork.getDockerClient().restartNode(node.getContainerName());
        logger.info("Restarted (docker restart) node {}", node);
    }

    public void stopNode(RadixNode node) {
        radixNetwork.getDockerClient().stopNode(node.getContainerName());
        logger.info("Stopped (docker stop) node {}", node);
    }

    public void runCommand(RadixNode node, String command) {
        radixNetwork.getDockerClient().runCommand(node.getRootUrl(), command);
    }

    public void waitForNodeToBeUp(RadixNode node) {
        TestingUtils.waitForNodeToBeUp(node, radixNetwork.getConfiguration());
        logger.info("Node {} is UP", node);
    }

    @AfterAll
    public void teardown() {
        switch (radixNetwork.getConfiguration().getType()) {
            case LOCALNET:
                if (Boolean.parseBoolean(System.getenv("RADIXDLT_DOCKER_DO_NOT_WIPE_NETWORK"))) {
                    return;
                }
                radixNetwork.getDockerClient().cleanup();
                break;
            case TESTNET:
                break;
        }
    }

}
