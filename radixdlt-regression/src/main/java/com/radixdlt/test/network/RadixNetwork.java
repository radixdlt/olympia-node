package com.radixdlt.test.network;

import com.github.dockerjava.api.exception.DockerClientException;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.test.account.Account;
import com.radixdlt.test.network.client.RadixHttpClient;
import com.radixdlt.test.network.client.docker.DockerClient;
import com.radixdlt.test.network.client.docker.DisabledDockerClient;
import com.radixdlt.test.network.client.docker.LocalDockerClient;
import com.radixdlt.test.network.client.docker.LocalDockerNetworkCreator;
import com.radixdlt.test.network.client.docker.RemoteDockerClient;
import com.radixdlt.test.utils.universe.UniverseVariables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents an actual radix network with several running nodes. Keeps a list of the nodes, along with their addresses.
 * Has many utility functions. Used when running tests against real networks (acceptance or system tests).
 */
public class RadixNetwork {

    private static final Logger logger = LogManager.getLogger();

    private final RadixNetworkConfiguration configuration;
    private final int networkId;
    private final List<RadixNode> nodes;
    private final DockerClient dockerClient;
    private final RadixHttpClient httpClient;
    private final UniverseVariables universeVariables;

    private RadixNetwork(RadixNetworkConfiguration configuration, int networkId, List<RadixNode> nodes,
                         RadixHttpClient httpClient, DockerClient dockerClient, UniverseVariables universeVariables) {
        this.configuration = configuration;
        this.networkId = networkId;
        this.nodes = nodes;
        this.httpClient = httpClient;
        this.dockerClient = dockerClient;
        this.universeVariables = universeVariables;
    }

    public static RadixNetwork initializeFromEnv() {
        var configuration = RadixNetworkConfiguration.fromEnv();
        prettyPrintConfiguration(configuration);

        // if we are using a local network, we may need to create it and store the universe variables
        UniverseVariables universeVariables = null;
        if (configuration.getDockerConfiguration().shouldInitializeNetwork()
            && configuration.getType() == RadixNetworkConfiguration.Type.LOCALNET) {
            var localDockerClient = new LocalDockerClient(configuration.getDockerConfiguration());
            universeVariables = LocalDockerNetworkCreator.createNewLocalNetwork(configuration, localDockerClient);
        }

        var networkId = configuration.pingJsonRpcApi();
        logger.info("Connected to JSON RPC API at {}", configuration.getJsonRpcRootUrl());

        var dockerClient = createDockerClient(configuration);
        var httpClient = RadixHttpClient.fromRadixNetworkConfiguration(configuration);
        var radixNodes = RadixNetworkNodeLocator.locateNodes(configuration, httpClient, dockerClient);
        if (radixNodes == null || radixNodes.size() == 0) {
            throw new RuntimeException("No nodes found, cannot run tests");
        }

        logger.info("Done locating nodes, found {} in total.", radixNodes.size());
        radixNodes.forEach(node -> logger.debug(" - {}", node));
        return new RadixNetwork(configuration, networkId, radixNodes, httpClient, dockerClient, universeVariables);
    }

    public Account generateNewAccount() {
        return Account.initialize(configuration.getJsonRpcRootUrl(), configuration.getPrimaryPort(), configuration.getSecondaryPort());
    }

    /**
     * Calls the faucet to send tokens to the given address
     */
    public String faucet(AccountAddress to) {
        var faucets = nodes.stream().filter(node -> node.getAvailableServices()
            .contains(RadixNode.ServiceType.FAUCET)).collect(Collectors.toList());
        if (faucets.isEmpty()) {
            throw new NoFaucetException("No faucet found in this network");
        }
        var nodeWithFaucet = faucets.get(0);
        var address = to.toString(networkId);
        var txID = httpClient.callFaucet(nodeWithFaucet.getRootUrl(), nodeWithFaucet.getSecondaryPort(), address);
        logger.debug("Faucet successfully sent tokens to {}. TxID: {}", address, txID);
        return txID;
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public List<RadixNode> getNodes() {
        return nodes;
    }

    public RadixNetworkConfiguration getConfiguration() {
        return configuration;
    }

    public UniverseVariables getUniverseVariables() {
        return universeVariables;
    }

    private static void prettyPrintConfiguration(RadixNetworkConfiguration configuration) {
        logger.debug("Network configuration:");
        logger.debug("JSON-RPC URL: {}, type: {}", configuration.getJsonRpcRootUrl(), configuration.getType());
    }

    private static DockerClient createDockerClient(RadixNetworkConfiguration configuration) {
        var dockerConfiguration = configuration.getDockerConfiguration();
        DockerClient dockerClient;
        try {
            switch (configuration.getType()) {
                case LOCALNET:
                    dockerClient = new LocalDockerClient(dockerConfiguration);
                    break;
                case TESTNET:
                    dockerClient = new RemoteDockerClient(configuration);
                    break;
                default:
                    dockerClient = new DisabledDockerClient();
            }
            logger.debug("Initialized a {} docker client", configuration.getType());
        } catch (DockerClientException e) {
            logger.warn("Exception {} when trying to initialize a docker client. Client will be disabled.", e.getMessage(), e);
            dockerClient = new DisabledDockerClient();
        }
        return dockerClient;
    }

}
