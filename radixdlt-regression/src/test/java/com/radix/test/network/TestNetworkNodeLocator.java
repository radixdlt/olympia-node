package com.radix.test.network;

import com.radix.test.network.client.HttpException;
import com.radix.test.network.client.RadixHttpClient;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.api.sync.RadixApi;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Lists;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * TODO
 */
public class TestNetworkNodeLocator {

    private static final Logger logger = LogManager.getLogger();

    private static final int MAX_EXPECTED_LOCALNET_NODES = 3;

    private TestNetworkNodeLocator() {

    }

    public static List<TestNode> findNodes(TestNetworkConfiguration configuration, RadixHttpClient nodeApi) {
        var jsonRpcApiRootUrl = configuration.getJsonRpcRootUrl().toExternalForm();
        var peers = ImperativeRadixApi.connect(jsonRpcApiRootUrl, 443, 443).network().peers();
        switch (configuration.getType()) {
            case LOCALNET:
                logger.debug("Locating ({}) nodes from local network...", peers.size());
                var nodeUrls = createLocalUrlList(jsonRpcApiRootUrl);
                return locateNodes(nodeApi, nodeUrls);
            case TESTNET:
                logger.debug("Locating ({}) nodes from testnet via node at {}", peers.size(), jsonRpcApiRootUrl);
//                nodeUrls = createRemoteUrlList(configuration.getNodeApiRootUrl(), nodeApi).stream()
//                    .map(url -> getUrlPairFromUrl(url, configuration.getNodeApiRootUrl().getPort()))
//                    .collect(Collectors.toList());
//                return locateNodes(nodeApi, nodeUrls);
                return null;
        }
        return Lists.newArrayList();
    }

    private static Pair<String, String> getUrlPairFromUrl(URL url, int port) {
        var nodeApiRootUrl = String.format("%s://%s%s:%s", url.getProtocol(),
            url.getHost(), url.getPath(), port);
        return Pair.of(url.toExternalForm(), nodeApiRootUrl);
    }

    private static List<URL> createRemoteUrlList(URL nodeApiRootUrl, RadixHttpClient nodeApi) {
        return nodeApi.getPeers(nodeApiRootUrl);
    }

    private static List<Pair<String, String>> createLocalUrlList(String jsonRpcRootUrl) {
        return null;

//        return IntStream.range(0, MAX_EXPECTED_LOCALNET_NODES).mapToObj(counter -> {
//            return null;
//        });
    }

    private static List<TestNode> locateNodes(RadixHttpClient nodeApi, List<Pair<String, String>> nodeUrlPairs) {
        return nodeUrlPairs.stream().map(nodeUrlPair -> {
            var nodeApiRootUrl = nodeUrlPair.getFirst();
            var jsonRpcRootUrl = nodeUrlPair.getSecond();
            var faucetRootUrl = "";

            // check for node api accessibility
            try {
                nodeApi.getNodeInfo(nodeApiRootUrl);
            } catch (HttpException e) {
                // this node doesn't have a node api accessible
                nodeApiRootUrl = null;
            }

            // get json-rpc api availability
            try {
                pingJsonRpcEndpoint(jsonRpcRootUrl);
            } catch (HttpException e) {
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
            } catch (HttpException e) {
                logger.error(e.getMessage());
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
        RadixApi.connect(jsonRpcRootUrl).flatMap(synchronousRadixApiClient -> synchronousRadixApiClient.network().id())
            .onFailureDo(() -> {
                throw new HttpException("Could not connect to JSON-RPC API at " + jsonRpcRootUrl + "");
            });
    }
}
