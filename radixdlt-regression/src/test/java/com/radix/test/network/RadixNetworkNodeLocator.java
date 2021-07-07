package com.radix.test.network;

import com.radix.test.network.client.HttpException;
import com.radix.test.network.client.RadixHttpClient;
import com.radixdlt.application.system.construction.FeeReserveNotEnoughBalanceException;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.client.lib.dto.NetworkId;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Sets;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * TODO
 */
public class RadixNetworkNodeLocator {

    private static final Logger logger = LogManager.getLogger();

    private RadixNetworkNodeLocator() {

    }

    public static List<RadixNode> findNodes(RadixNetworkConfiguration configuration, RadixHttpClient httpClient) {
        var jsonRpcApiRootUrl = configuration.getJsonRpcRootUrl();
        var peers = configuration.connect().network().peers();
        switch (configuration.getType()) {
            case LOCALNET:
                logger.debug("Locating ({}) nodes from local network...", peers.size()+1);
                return locateLocalNodes(configuration, httpClient, peers.size()+1);
            case TESTNET:
                logger.debug("Locating ({}) nodes from testnet via node at {}", peers.size()+1, jsonRpcApiRootUrl);
            default:
                throw new RuntimeException("Unimplemented!");
        }
    }

    private static List<RadixNode> locateLocalNodes(RadixNetworkConfiguration configuration, RadixHttpClient httpClient,
                                                    int expectedNoOfNodes) {
        return IntStream.range(0, expectedNoOfNodes).mapToObj(counter -> {
            return figureOutNode(configuration, httpClient);
        }).collect(Collectors.toList());
    }

    /**
     * What to do when 1) node lacks an endpoint 2) node doesn't exist
     */
    private static RadixNode figureOutNode(RadixNetworkConfiguration configuration, RadixHttpClient httpClient) {
        Set<RadixNode.ServiceType> availableNodeServices = Sets.newHashSet();

        var api = ImperativeRadixApi.connect(configuration.getJsonRpcRootUrl(),
            configuration.getPrimaryPort(), configuration.getSecondaryPort());
        var networkId = api.network().id().getNetworkId();
        availableNodeServices.add(RadixNode.ServiceType.ARCHIVE);

        var randomAddress = AccountAddress.create(ECKeyPair.generateNew().getPublicKey());
        api.account().balances(randomAddress);
        availableNodeServices.add(RadixNode.ServiceType.ACCOUNT);

        try {
            api.transaction().build(TransactionRequest.createBuilder(randomAddress).build());
        } catch (RadixApiException e) {
            // this is expected since the random address has no funds. However, it tells us that /construction is available
            if (!(e.getMessage().contains(FeeReserveNotEnoughBalanceException.class.getSimpleName()))) {
                throw e;
            }
        }
        availableNodeServices.add(RadixNode.ServiceType.CONSTRUCTION);

        api.api().data();
        availableNodeServices.add(RadixNode.ServiceType.SYSTEM);

        api.local().validatorInfo();
        availableNodeServices.add(RadixNode.ServiceType.VALIDATION);

        httpClient.callFaucet(configuration.getJsonRpcRootUrl(), configuration.getSecondaryPort(),
            randomAddress.toString(networkId));
        availableNodeServices.add(RadixNode.ServiceType.FAUCET);

        return new RadixNode(availableNodeServices);
    }

}
