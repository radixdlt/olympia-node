package com.radixdlt.test.network.client.docker;

/**
 * Connects to some docker daemon(s) and executes docker commands
 */
public interface DockerClient {

    /**
     * will try to connect to some docker daemon
     */
    void connect();

    String runCommand(String nodeLocator, String... commands);

    void restartNode(String nodeLocator);

    void stopNode(String nodeLocator);

}
