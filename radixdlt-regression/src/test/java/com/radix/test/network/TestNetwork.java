package com.radix.test.network;

import com.mashape.unirest.http.exceptions.UnirestException;
import com.radix.test.account.Account;
import com.radix.test.network.client.NodeApiClient;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.utils.functional.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TODO
 */
public class TestNetwork {

    private static final Logger logger = LogManager.getLogger();

    private final TestNetworkConfiguration configuration;
    private final List<TestNode> testNodes;
    private final NodeApiClient nodeApi;

    private TestNetwork(TestNetworkConfiguration configuration, List<TestNode> testNodes, NodeApiClient nodeApi) {
        this.configuration = configuration;
        this.testNodes = testNodes;
        this.nodeApi = nodeApi;
    }

    public static TestNetwork initializeFromEnv() {
        var configuration = TestNetworkConfiguration.fromEnv();
        prettyPrintConfiguration(configuration);

        // make sure JSON-RPC is up and running
        var urlString = configuration.getJsonRpcRootUrl().toExternalForm();
        TestNetworkNodeLocator.pingJsonRpcEndpoint(urlString);
        logger.info("Connected to {}", urlString);

        // actually figure out what nodes this network has
        NodeApiClient nodeApi = NodeApiClient.fromTestNetworkConfiguration(configuration);
        List<TestNode> testNodes = TestNetworkNodeLocator.findNodes(configuration, nodeApi);
        if (testNodes == null || testNodes.size() == 0) {
            throw new RuntimeException("No nodes found, cannot test");
        }
        logger.info("Found {} modes", testNodes.size());
        testNodes.forEach(logger::debug);

        return new TestNetwork(configuration, testNodes, nodeApi);
    }

    private static void prettyPrintConfiguration(TestNetworkConfiguration configuration) {
        logger.info("Will locate test nodes from properties:");
        logger.info("JSON-RPC URL: {}", configuration.getJsonRpcRootUrl());
        logger.info("Node API URL: {}", configuration.getNodeApiRootUrl());
        logger.info("Network type: {}", configuration.getType());
    }

    public Result<Account> generateNewAccount() {
        return Account.initialize(configuration.getJsonRpcRootUrl().toExternalForm());
    }

    /**
     * will call the faucet once for this address
     *
     * @return txID if successful - null if unsuccessful
     */
    public String faucet(AccountAddress address) {
        List<TestNode> faucets = testNodes.stream().filter(TestNode::isFaucetEnabled).collect(Collectors.toList());
        if (faucets.size() == 0) {
            logger.warn("No faucets found on network");
            return null;
        }
        TestNode nodeWithFaucet = faucets.get(0);
        String faucetRootUrl = nodeWithFaucet.getFaucetRootUrl();
        try {
            String txID = nodeApi.callFaucet(faucetRootUrl, address);
            logger.debug("Faucet at {} successfully called ({})", faucetRootUrl, txID);
            return txID;
        } catch (UnirestException e) {
            logger.error("Error calling faucet {}: {}", faucetRootUrl, e.getMessage());
            throw new RuntimeException("Test fail!");
        }
    }

}
