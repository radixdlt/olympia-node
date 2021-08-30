package com.radixdlt.test.network.client.docker;

import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.radixdlt.test.utils.universe.ValidatorKeypair;

import java.util.List;

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

    /**
     * @param networkName will also connect the container to the given network
     */
    void startNewNode(String image, String containerName, List<String> environment, HostConfig hostConfig, List<ExposedPort> exposedPorts,
                      String networkName);

    void createNetwork(String networkName);

}
