package com.radixdlt.test.network;


import com.radixdlt.test.utils.TestingUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Configuration properties for using docker in the context of a radix (test) network
 */
@Getter
@AllArgsConstructor
public class DockerConfiguration {

    private final String socketUrl;
    private final String containerName;
    @Accessors(fluent = true)
    private final boolean shouldInitializeNetwork;
    private final String image;
    private final int initialNumberOfNodes;
    private final String networkName;

    public static DockerConfiguration fromEnv() {
        var socketUrl = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_SOCKET_URL", "unix:///var/run/docker.sock");
        var containerName = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_CONTAINER_NAME", "docker_core%d_1");
        if (!containerName.contains("%d")) {
            throw new RuntimeException("Docker container name needs to contain a '%d' wildcard e.g. docker_core%d_1");
        }
        var shouldInitializeNetworkString = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_INITIALIZE_NETWORK", "false");
        var shouldInitializeNetwork = Boolean.parseBoolean(shouldInitializeNetworkString);
        var image = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_IMAGE", "radixdlt/radixdlt-core:develop");
        var initialNumberOfNodesString = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_INITIAL_NUMBER_OF_NODES", "3");
        var initialNumberOfNodes = Integer.parseInt(initialNumberOfNodesString);
        var networkName = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_NETWORK_NAME", "system_testing_network");
        return new DockerConfiguration(socketUrl, containerName, shouldInitializeNetwork, image, initialNumberOfNodes, networkName);
    }

}
