package com.radix.test.network;

import com.radix.test.account.Account;
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
    private final List<RadixNode> radixNodes;
    private final RadixHttpClient nodeApi;

    private RadixNetwork(RadixNetworkConfiguration configuration, List<RadixNode> radixNodes, RadixHttpClient nodeApi) {
        this.configuration = configuration;
        this.radixNodes = radixNodes;
        this.nodeApi = nodeApi;
    }

    public static RadixNetwork initializeFromEnv() {
        var configuration = RadixNetworkConfiguration.fromEnv();
        prettyPrintConfiguration(configuration);

        configuration.pingJsonRpcApi();
        logger.info("Connected to JSON RPC API at {}", configuration.getJsonRpcRootUrl());

        // figure out the nodes of this network, and the services they expose
        RadixHttpClient httpClient = RadixHttpClient.fromRadixNetworkConfiguration(configuration);
        List<RadixNode> radixNodes = RadixNetworkNodeLocator.findNodes(configuration, httpClient);
        if (radixNodes == null || radixNodes.size() == 0) {
            throw new RuntimeException("No nodes found, cannot test");
        }
        logger.info("Located {} radix nodes", radixNodes.size());
        radixNodes.forEach(logger::debug);

        return new RadixNetwork(configuration, radixNodes, httpClient);
    }

    private static void prettyPrintConfiguration(RadixNetworkConfiguration configuration) {
        logger.debug("Will locate test nodes from properties:");
        logger.debug("JSON-RPC URL: {}", configuration.getJsonRpcRootUrl());
        logger.debug("Network type: {}", configuration.getType());
    }

    public Result<Account> generateNewAccount() {
        return Account.initialize(configuration.getJsonRpcRootUrl());
    }

}
