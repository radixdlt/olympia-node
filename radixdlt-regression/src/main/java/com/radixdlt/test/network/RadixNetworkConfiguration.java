package com.radixdlt.test.network;

import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.test.utils.TestingUtils;
import org.awaitility.Durations;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;

/**
 * Information needed for the initialization of a {@link RadixNetwork}
 */
public class RadixNetworkConfiguration {

    enum Type {
        LOCALNET,
        TESTNET;
    }

    private final String jsonRpcRootUrl;
    private final int primaryPort;
    private final int secondaryPort;
    private final String basicAuth;
    private final Type type;
    private final DockerConfiguration dockerConfiguration;

    private RadixNetworkConfiguration(String jsonRpcRootUrl, int primaryPort, int secondaryPort, String basicAuth,
                                      Type type, DockerConfiguration dockerConfiguration) {
        this.jsonRpcRootUrl = jsonRpcRootUrl;
        this.primaryPort = primaryPort;
        this.secondaryPort = secondaryPort;
        this.basicAuth = basicAuth;
        this.type = type;
        this.dockerConfiguration = dockerConfiguration;
    }

    public static RadixNetworkConfiguration fromEnv() {
        try {
            var jsonRpcRootUrlString = TestingUtils.getEnvWithDefault("RADIXDLT_JSON_RPC_API_ROOT_URL", "http://localhost");
            var jsonRpcRootUrl = new URL(jsonRpcRootUrlString);
            var primaryPort = (jsonRpcRootUrl.getProtocol().equalsIgnoreCase("https")) ? 443
                : Integer.parseInt(TestingUtils.getEnvWithDefault("RADIXDLT_JSON_RPC_API_PRIMARY_PORT", "8080"));
            var secondaryPort = (jsonRpcRootUrl.getProtocol().equalsIgnoreCase("https")) ? 443
                : Integer.parseInt(TestingUtils.getEnvWithDefault("RADIXDLT_JSON_RPC_API_SECONDARY_PORT", "3333"));
            var basicAuth = System.getenv("RADIXDLT_BASIC_AUTH");
            var type = determineType(jsonRpcRootUrlString);
            var dockerConfiguration = DockerConfiguration.fromEnv();
            // TODO check if docker network should be initialized but type is NOT local
            return new RadixNetworkConfiguration(jsonRpcRootUrlString, primaryPort, secondaryPort, basicAuth, type,
                dockerConfiguration);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Bad JSON-RPC URL", e);
        }
    }

    public DockerConfiguration getDockerConfiguration() {
        return dockerConfiguration;
    }

    private static Type determineType(String jsonRpcUrlString) {
        return jsonRpcUrlString.toLowerCase().contains("localhost") || jsonRpcUrlString.contains("127.0.0.1")
            ? Type.LOCALNET : Type.TESTNET;
    }

    public ImperativeRadixApi connect() {
        return ImperativeRadixApi.connect(jsonRpcRootUrl, primaryPort, secondaryPort);
    }

    /**
     * Tries to connect and call the "networkId" method, making sure that we have a working json-rpc api
     *
     * @return the network id
     */
    public int pingJsonRpcApi() {
        AtomicInteger networkId = new AtomicInteger();
        await().atMost(Durations.ONE_MINUTE).ignoreException(RadixApiException.class).until(() -> {
            networkId.set(ImperativeRadixApi.connect(jsonRpcRootUrl, primaryPort, secondaryPort).network().id().getNetworkId());
            TestingUtils.sleepMillis(250);
            return true;
        });
        return networkId.intValue();
    }

    public String getJsonRpcRootUrl() {
        return jsonRpcRootUrl;
    }

    public int getPrimaryPort() {
        return primaryPort;
    }

    public int getSecondaryPort() {
        return secondaryPort;
    }

    public String getBasicAuth() {
        return basicAuth;
    }

    public Type getType() {
        return type;
    }

}
