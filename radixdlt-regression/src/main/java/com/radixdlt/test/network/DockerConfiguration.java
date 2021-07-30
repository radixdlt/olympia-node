package com.radixdlt.test.network;


import com.radixdlt.test.utils.TestingUtils;

/**
 * Configuration properties for using docker in the context of a radix (test) network
 */
public class DockerConfiguration {

    private final String socketUrl;
    private String containerName;

    private DockerConfiguration(String socketUrl, String containerName) {
        this.socketUrl = socketUrl;
        this.containerName = containerName;
    }

    public static DockerConfiguration fromEnv() {
        var socketUrl = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_SOCKET_URL", "unix:///var/run/docker.sock");
        var containerName = TestingUtils.getEnvWithDefault("RADIXDLT_DOCKER_CONTAINER_NAME", "docker_core%d_1");
        if (!containerName.contains("%d")) {
            throw new RuntimeException("Docker container name needs to contain a '%d' wildcard e.g. docker_core%d_1");
        }
        return new DockerConfiguration(socketUrl, containerName);
    }

    public String getSocketUrl() {
        return socketUrl;
    }

    public String getContainerName() {
        return containerName;
    }
}
