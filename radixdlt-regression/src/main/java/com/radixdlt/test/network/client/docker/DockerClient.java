package com.radixdlt.test.network.client.docker;

/**
 * Connects to some docker daemon(s) and executes docker commands
 */
public interface DockerClient {

    String runCommand(String nodeLocator, String... commands);

    void restartNode(String nodeLocator);

    void stopNode(String nodeLocator);

    /**
     * restores the network to its original state, if possible
     *
     * @param cleanupParameters extra information needed for cleanup
     */
    void cleanup(String... cleanupParameters);

}
