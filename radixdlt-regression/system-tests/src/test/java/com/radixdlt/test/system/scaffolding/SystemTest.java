package com.radixdlt.test.system.scaffolding;

import com.radixdlt.test.RadixNetworkTest;
import com.radixdlt.test.network.RadixNode;
import com.radixdlt.test.network.client.docker.LocalDockerNetworkCreator;
import com.radixdlt.test.utils.TestingUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

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
        throw new RuntimeException("Unimplemented");
    }

    public void waitForNodeToBeUp(RadixNode node) {
        TestingUtils.waitForNodeToBeUp(node, radixNetwork.getConfiguration());
        logger.info("Node {} is UP", node);
    }

    @AfterEach
    public void teardown() {
        switch (radixNetwork.getConfiguration().getType()) {
            case LOCALNET:
                if (Boolean.parseBoolean(System.getenv("RADIXDLT_DOCKER_DO_NOT_WIPE_NETWORK"))) {
                    return;
                }
                String networkName = radixNetwork.getConfiguration().getDockerConfiguration().getNetworkName();
                LocalDockerNetworkCreator.wipeLocalNetwork(radixNetwork.getConfiguration(), radixNetwork.getDockerClient());
                logger.info("Wiped docker network '{}'", networkName);
                break;
            case TESTNET:
                break;
        }
    }

}
