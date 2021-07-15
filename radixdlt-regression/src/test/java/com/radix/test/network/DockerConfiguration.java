package com.radix.test.network;

import com.radix.test.Utils;

/**
 * Configuration properties for using docker in the context of a radix (test) network
 */
public class DockerConfiguration {

    private final String socketUrl;

    private DockerConfiguration(String socketUrl) {
        this.socketUrl = socketUrl;
    }

    public static DockerConfiguration fromEnv() {
        var socketUrl = Utils.getEnvWithDefault("RADIXDLT_DOCKER_SOCKET_URL", "unix:///var/run/docker.sock");
        return new DockerConfiguration(socketUrl);
    }

    public String getSocketUrl() {
        return socketUrl;
    }

}
