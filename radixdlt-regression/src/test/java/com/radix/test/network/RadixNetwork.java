package com.radix.test.network;

import com.radix.test.account.Account;
import com.radix.test.docker.DockerClient;
import com.radix.test.docker.LocalDockerClient;
import com.radix.test.network.client.RadixHttpClient;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.utils.functional.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents an actual radix network with several running nodes. Keeps a list of the nodes, along with their addresses.
 * Used when running tests against real networks (acceptance or system tests).
 */
public class RadixNetwork {

    private static final Logger logger = LogManager.getLogger();

    private final RadixNetworkConfiguration configuration;
    private final int networkId;
    private final List<RadixNode> nodes;
    private final DockerClient dockerClient;
    private final RadixHttpClient httpClient;

    private RadixNetwork(RadixNetworkConfiguration configuration, int networkId, List<RadixNode> nodes,
                         RadixHttpClient httpClient, DockerClient dockerClient) {
        this.configuration = configuration;
        this.networkId = networkId;
        this.nodes = nodes;
        this.httpClient = httpClient;
        this.dockerClient = dockerClient;
    }

    public static RadixNetwork initializeFromEnv() {
        var configuration = RadixNetworkConfiguration.fromEnv();
        prettyPrintConfiguration(configuration);

        int networkId = configuration.pingJsonRpcApi();
        logger.info("Connected to JSON RPC API at {}", configuration.getJsonRpcRootUrl());

        RadixHttpClient httpClient = RadixHttpClient.fromRadixNetworkConfiguration(configuration);
        DockerClient dockerClient = createDockerClient(configuration);
        List<RadixNode> radixNodes = RadixNetworkNodeLocator.locateNodes(configuration, httpClient, dockerClient);
        if (radixNodes == null || radixNodes.size() == 0) {
            throw new RuntimeException("No nodes found, cannot run tests");
        }

        logger.info("Located {} nodes", radixNodes.size());
        radixNodes.forEach(logger::debug);

        return new RadixNetwork(configuration, networkId, radixNodes, httpClient, dockerClient);
    }

    private static void prettyPrintConfiguration(RadixNetworkConfiguration configuration) {
        logger.debug("Network configuration:");
        logger.debug("JSON-RPC URL: {}, type: {}", configuration.getJsonRpcRootUrl(), configuration.getType());
    }

    private static DockerClient createDockerClient(RadixNetworkConfiguration configuration) {
        var dockerConfiguration = configuration.getDockerConfiguration();
        DockerClient dockerClient;
        switch (configuration.getType()) {
            case LOCALNET:
                dockerClient = new LocalDockerClient(dockerConfiguration.getSocketUrl());
                break;
            case TESTNET:
            default:
                throw new RuntimeException("Unimplemented");
        }
        dockerClient.connect();
        logger.debug("Successfully initialized a {} docker client", configuration.getType());
        return dockerClient;
    }

    public Result<Account> generateNewAccount() {
        return Account.initialize(configuration.getJsonRpcRootUrl());
    }

    public void faucet(AccountAddress to) {
        List<RadixNode> faucets = nodes.stream().filter(node -> node.getAvailableServices()
            .contains(RadixNode.ServiceType.FAUCET)).collect(Collectors.toList());
        RadixNode nodeWithFaucet = faucets.get(0);
        String address = to.toString(networkId);
        String txID = httpClient.callFaucet(nodeWithFaucet.getRootUrl(), nodeWithFaucet.getSecondaryPort(), address);
        logger.debug("Successfully called faucet to send tokens to {}. TxID: {}", address, txID);
    }

}
