package com.radix.test.network.client.docker;

import com.github.dockerjava.api.exception.DockerException;

/**
 * Connects to some docker daemon(s) and executes docker commands
 */
public interface DockerClient {

    /**
     * will try to connect to the docker daemon
     *
     * @throws DockerException
     */
    void connect();

    String runShellCommandAndGetOutput(String containerId, String... commands);

}
