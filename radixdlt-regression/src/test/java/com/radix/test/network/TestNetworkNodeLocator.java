package com.radix.test.network;

import com.mashape.unirest.http.exceptions.UnirestException;
import com.radix.test.network.client.NodeApiClient;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.impl.SynchronousRadixApiClient;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestNetworkNodeLocator {

    private static final Logger logger = LogManager.getLogger();

    private static final int MAX_EXPECTED_LOCALNET_NODES = 3;

    private TestNetworkNodeLocator() {

    }

    public static List<TestNode> findNodes(TestNetworkConfiguration configuration, NodeApiClient nodeApi) {
        switch (configuration.getType()) {
            case LOCALNET_WITHOUT_DOCKER:
                logger.debug("Locating nodes from local network...");
                var nodeUrls = createLocalUrlList(configuration.getNodeApiRootUrl(),
                        configuration.getJsonRpcRootUrl());
                return locateNodes(nodeApi, nodeUrls);
            case LOCALNET_DOCKER:
            case TESTNET:
                throw new RuntimeException("Unimplemented network type " + configuration.getType());
        }
        return null;
    }

    private static List<Pair<String, String>> createLocalUrlList(URL nodeApiRootUrl, URL jsonRpcRootUrl) {
        return IntStream.range(0, MAX_EXPECTED_LOCALNET_NODES).mapToObj(counter -> {
            var newNodeApiPort = nodeApiRootUrl.getPort() + counter;
            var newJsonRpcPort = jsonRpcRootUrl.getPort() + counter;
            var newNodeApiRootUrl = String.format("%s://%s%s:%s", nodeApiRootUrl.getProtocol(),
                    nodeApiRootUrl.getHost(), nodeApiRootUrl.getPath(), newNodeApiPort);
            var newJsonRpcRootUrl = String.format("%s://%s%s:%s", jsonRpcRootUrl.getProtocol(),
                    jsonRpcRootUrl.getHost(), jsonRpcRootUrl.getPath(), newJsonRpcPort);
            return Pair.of(newNodeApiRootUrl, newJsonRpcRootUrl);
        }).collect(Collectors.toList());
    }

    private static List<TestNode> locateNodes(NodeApiClient nodeApi, List<Pair<String, String>> nodeUrlPairs) {
        return nodeUrlPairs.stream().map(nodeUrlPair -> {
            var nodeApiRootUrl = nodeUrlPair.getFirst();
            var jsonRpcRootUrl = nodeUrlPair.getSecond();
            var faucetRootUrl = "";

            // check for node api accessibility
            try {
                nodeApi.getNodeInfo(nodeApiRootUrl);
            } catch (UnirestException e) {
                // this node doesn't have a node api accessible
                nodeApiRootUrl = null;
            }

            // get json-rpc api availability
            try {
                pingJsonRpcEndpoint(jsonRpcRootUrl);
            } catch (RuntimeException e) {
                // this node doesn't have a json rpc api accessible
                jsonRpcRootUrl = null;
            }

            // is there a faucet?
            try {
                // we expect the faucet to be under the same root as the node api. This might break.
                String originalNodeApiRootUrl = nodeUrlPair.getFirst();
                AccountAddress address = AccountAddress.create(ECKeyPair.generateNew().getPublicKey());
                nodeApi.callFaucet(originalNodeApiRootUrl, address);
                faucetRootUrl = originalNodeApiRootUrl;
                logger.debug("Found a faucet at {}", originalNodeApiRootUrl);
            } catch (UnirestException e) {
                // this node doesn't have a faucet
            }

            // get docker container ID via a docker client
            // TODO implement later

            return TestNode.create(jsonRpcRootUrl, nodeApiRootUrl, faucetRootUrl, null);
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Connects and calls the "networkId" method, making sure that there is a responsive json-rpc api
     */
    public static void pingJsonRpcEndpoint(String jsonRpcRootUrl) {
        var networkIdDTOResult = SynchronousRadixApiClient.connect(jsonRpcRootUrl)
                .flatMap(SynchronousRadixApiClient::networkId);
        if (!networkIdDTOResult.isSuccess()) {
            throw new RuntimeException("Could not connect to JSON-RPC API");
        }
    }

}
