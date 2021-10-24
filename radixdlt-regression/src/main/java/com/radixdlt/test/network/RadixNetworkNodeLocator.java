package com.radixdlt.test.network;

import com.github.dockerjava.api.exception.DockerException;
import com.google.common.collect.Sets;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.rpc.BasicAuth;
import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.client.lib.dto.NetworkPeer;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.test.network.client.RadixHttpClient;
import com.radixdlt.test.network.client.docker.DockerClient;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Has utilities for scanning and locating nodes in radix networks
 */
public class RadixNetworkNodeLocator {

    private static final Logger logger = LogManager.getLogger();

    private RadixNetworkNodeLocator() {

    }

    public static List<RadixNode> locateNodes(RadixNetworkConfiguration configuration, RadixHttpClient httpClient,
                                              DockerClient dockerClient) {
        List<RadixNode> radixNodes = Lists.newArrayList();
        tryAddFaucetToNodeList(configuration, radixNodes);

        List<NetworkPeer> peers = Lists.newArrayList();
        try {
            if (StringUtils.isBlank(configuration.getBasicAuth())) {
                peers = configuration.connect(Optional.empty()).network().peers();
            } else {
                var credentials = configuration.getBasicAuth().split(":");
                var basicAuth = BasicAuth.with(credentials[0], credentials[1]);
                peers = configuration.connect(Optional.of(basicAuth)).network().peers();
            }
        } catch (RadixApiException e) {
            if (e.getMessage().toLowerCase().contains("401 authorization required")) {
                logger.warn("Could not fetch peers list from {} due to 401. The test will use only one archive node.",
                    configuration.getJsonRpcRootUrl());
                addSingleNodePeerToList(configuration, radixNodes);
                return radixNodes;
            }
        }

        var peersSizePlusOne = peers.size() + 1;
        switch (configuration.getType()) {
            case LOCALNET:
                logger.debug("Searching for {} local nodes...", peersSizePlusOne);
                return locateLocalNodes(configuration, httpClient, dockerClient, peersSizePlusOne);
            case TESTNET:
            default:
                logger.debug("Searching for {} testnet nodes", peersSizePlusOne);
                throw new RuntimeException("Unimplemented"); // get a list of RadixNodes by parsing the peers list
        }
    }

    private static void tryAddFaucetToNodeList(RadixNetworkConfiguration configuration, List<RadixNode> radixNodes) {
        if (StringUtils.isNotBlank(configuration.getFaucetUrl())) {
            // these ports might be wrong. Faucet config should be more robust
            Set<RadixNode.ServiceType> availableNodeServices = Sets.newHashSet();
            availableNodeServices.add(RadixNode.ServiceType.FAUCET);
            var faucetNode = new RadixNode(configuration.getFaucetUrl(), configuration.getPrimaryPort(),
                configuration.getSecondaryPort(), configuration.getDockerConfiguration().getContainerName(),
                availableNodeServices);
            radixNodes.add(faucetNode);
            var rootUrl = configuration.getFaucetUrl();
            var networkId = configuration.connect(Optional.empty()).network().id().getNetworkId();
            var randomAddress = AccountAddress.create(ECKeyPair.generateNew().getPublicKey()).toString(networkId);

            RadixHttpClient.fromRadixNetworkConfiguration(configuration).callFaucet(rootUrl, configuration.getPrimaryPort(),
                randomAddress);
            logger.info("Found a primary faucet at {}", configuration.getFaucetUrl());
        }
    }

    private static void addSingleNodePeerToList(RadixNetworkConfiguration configuration, List<RadixNode> radixNodes) {
        Set<RadixNode.ServiceType> availableNodeServices = Sets.newHashSet();
        RadixNode singleNode = new RadixNode(configuration.getJsonRpcRootUrl(), configuration.getPrimaryPort(),
            configuration.getSecondaryPort(), configuration.getDockerConfiguration().getContainerName(),
            availableNodeServices);
        radixNodes.add(singleNode);
    }

    private static List<RadixNode> locateLocalNodes(RadixNetworkConfiguration configuration, RadixHttpClient httpClient,
                                                    DockerClient dockerClient, int expectedNoOfNodes) {
        var primaryPort = configuration.getPrimaryPort();
        var secondaryPort = configuration.getSecondaryPort();
        var dockerContainerName = configuration.getDockerConfiguration().getContainerName();
        return IntStream.range(0, expectedNoOfNodes).mapToObj(counter -> {
            var containerName = String.format(dockerContainerName, counter);
            return figureOutNode(configuration.getJsonRpcRootUrl(), primaryPort + counter, secondaryPort + counter,
                containerName, httpClient, dockerClient);
        }).collect(Collectors.toList());
    }

    /**
     * Tries to figure out which endpoints are available (e.g. /account, /construction) and also tries to use the
     * docker client to establish a connection to this node's container.
     * <p>
     * TODO handle exceptions better. Right now, the test will fail is anything goes wrong here
     */
    private static RadixNode figureOutNode(String jsonRpcRootUrl, int primaryPort, int secondaryPort,
                                           String expectedContainerName, RadixHttpClient httpClient, DockerClient dockerClient) {
        Set<RadixNode.ServiceType> availableNodeServices = Sets.newHashSet();

        var api = ImperativeRadixApi.connect(jsonRpcRootUrl, primaryPort, secondaryPort);
        var networkId = api.network().id().getNetworkId();
        availableNodeServices.add(RadixNode.ServiceType.ARCHIVE);

        var randomAddress = AccountAddress.create(ECKeyPair.generateNew().getPublicKey());
        api.account().balances(randomAddress);
        availableNodeServices.add(RadixNode.ServiceType.ACCOUNT);

        try {
            api.transaction().build(TransactionRequest.createBuilder(randomAddress).build());
        } catch (RadixApiException e) {
            // this is expected since the random address has no funds. However, it tells us that /construction is available
            if (!(e.getMessage().contains("Not enough balance"))) {
                throw e;
            }
        }
        availableNodeServices.add(RadixNode.ServiceType.CONSTRUCTION);

        api.api().data();
        availableNodeServices.add(RadixNode.ServiceType.SYSTEM);

        api.local().validatorInfo();
        availableNodeServices.add(RadixNode.ServiceType.VALIDATION);

        httpClient.callFaucet(jsonRpcRootUrl, secondaryPort, randomAddress.toString(networkId));
        availableNodeServices.add(RadixNode.ServiceType.FAUCET);

        // check that the container name is correct.
        try {
            dockerClient.runCommand(expectedContainerName, "pwd");
        } catch (DockerException e) {
            logger.trace("Docker client could not connect due to {} and will be disabled.", e.getMessage());
        }

        return new RadixNode(jsonRpcRootUrl, primaryPort, secondaryPort, expectedContainerName, availableNodeServices);
    }

}
