package com.radixdlt.test.network;


import com.radixdlt.test.utils.TestingUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Configuration properties for using docker in the context of a radix (test) network
 */
public class DockerConfiguration {
    private static final Logger logger = LogManager.getLogger();

    private final String socketUrl;
    private final String containerName;
    private final boolean shouldInitializeNetwork;
    private final String image;
    private final int initialNumberOfNodes;
    private final String networkName;

    public DockerConfiguration(String socketUrl, String containerName, boolean shouldInitializeNetwork, String image, int initialNumberOfNodes,
                               String networkName) {
        this.socketUrl = socketUrl;
        this.containerName = containerName;
        this.shouldInitializeNetwork = shouldInitializeNetwork;
        this.image = image;
        this.initialNumberOfNodes = initialNumberOfNodes;
        this.networkName = networkName;
    }

    public static DockerConfiguration fromEnv() {
        var socketUrl = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_DAEMON_URL", "unix:///var/run/docker.sock");
        var containerName = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_CONTAINER_NAME", "system-testing-core%d");
        if (!containerName.contains("%d")) {
            throw new RuntimeException("Docker container name needs to contain a '%d' wildcard e.g. docker_core%d_1");
        } else if (containerName.contains("_")) {
            logger.warn("Underscores in container names ({}} should be avoided", containerName);
        }
        var shouldInitializeNetworkString = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_INITIALIZE_NETWORK", "false");
        var shouldInitializeNetwork = Boolean.parseBoolean(shouldInitializeNetworkString);
        var image = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_IMAGE", "radixdlt/radixdlt-core:develop");
        var initialNumberOfNodesString = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_INITIAL_NUMBER_OF_NODES", "3");
        var initialNumberOfNodes = Integer.parseInt(initialNumberOfNodesString);
        var networkName = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_NETWORK_NAME", "system_testing_network");
        return new DockerConfiguration(socketUrl, containerName, shouldInitializeNetwork, image, initialNumberOfNodes, networkName);
    }

    public String getNetworkName() {
        return networkName;
    }

    public String getSocketUrl() {
        return socketUrl;
    }

    public String getContainerName() {
        return containerName;
    }

    public boolean shouldInitializeNetwork() {
        return shouldInitializeNetwork;
    }

    public String getImage() {
        return image;
    }

    public int getInitialNumberOfNodes() {
        return initialNumberOfNodes;
    }

}
