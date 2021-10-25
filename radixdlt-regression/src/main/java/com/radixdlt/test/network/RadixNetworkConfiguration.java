package com.radixdlt.test.network;

import com.radixdlt.client.lib.api.rpc.BasicAuth;
import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.test.utils.TestingUtils;
import com.radixdlt.utils.functional.Failure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Durations;
import org.awaitility.core.ConditionTimeoutException;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;

/**
 * Information needed for the initialization of a {@link RadixNetwork}
 */
public class RadixNetworkConfiguration {

    private static final Logger logger = LogManager.getLogger();

    private static final Duration NETWORK_PING_PATIENCE = Durations.ONE_MINUTE;

    public enum Type {
        LOCALNET,
        TESTNET;
    }

    private final String jsonRpcRootUrl;
    private final int primaryPort;
    private final int secondaryPort;
    private final String faucetUrl;
    private final String basicAuth;
    private final Type type;
    private final DockerConfiguration dockerConfiguration;
    private final SshConfiguration sshConfiguration;

    private RadixNetworkConfiguration(String jsonRpcRootUrl, int primaryPort, int secondaryPort, String faucetUrl, String basicAuth,
                                      DockerConfiguration dockerConfiguration, SshConfiguration sshConfiguration) {
        this.jsonRpcRootUrl = jsonRpcRootUrl;
        this.primaryPort = primaryPort;
        this.secondaryPort = secondaryPort;
        this.faucetUrl = faucetUrl;
        this.basicAuth = basicAuth;
        this.type = determineType(jsonRpcRootUrl);
        this.dockerConfiguration = dockerConfiguration;
        this.sshConfiguration = sshConfiguration;
        if (type != Type.LOCALNET && dockerConfiguration.shouldInitializeNetwork()) {
            logger.warn("Cannot initialize a {} type of network", type);
        }
    }

    public static RadixNetworkConfiguration fromEnv() {
        try {
            var jsonRpcRootUrlString = TestingUtils.getEnvWithDefault("RADIXDLT_JSON_RPC_API_ROOT_URL", "http://localhost");
            var jsonRpcRootUrl = new URL(jsonRpcRootUrlString);
            var primaryPort = (jsonRpcRootUrl.getProtocol().equalsIgnoreCase("https")) ? 443
                : Integer.parseInt(TestingUtils.getEnvWithDefault("RADIXDLT_JSON_RPC_API_PRIMARY_PORT", "8080"));
            var secondaryPort = (jsonRpcRootUrl.getProtocol().equalsIgnoreCase("https")) ? 443
                : Integer.parseInt(TestingUtils.getEnvWithDefault("RADIXDLT_JSON_RPC_API_SECONDARY_PORT", "3333"));
            var faucetUrl = TestingUtils.getEnvWithDefault("RADIXDLT_FAUCET_URL", "");
            var basicAuth = System.getenv("RADIXDLT_BASIC_AUTH");
            var dockerConfiguration = DockerConfiguration.fromEnv();
            var sshConfiguration = SshConfiguration.fromEnv();
            return new RadixNetworkConfiguration(jsonRpcRootUrlString, primaryPort, secondaryPort, faucetUrl, basicAuth,
                dockerConfiguration, sshConfiguration);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Bad JSON-RPC URL", e);
        }
    }

    public DockerConfiguration getDockerConfiguration() {
        return dockerConfiguration;
    }

    public SshConfiguration getSshConfiguration() {
        return sshConfiguration;
    }

    private static Type determineType(String jsonRpcUrlString) {
        return jsonRpcUrlString.toLowerCase().contains("localhost") || jsonRpcUrlString.contains("127.0.0.1")
            ? Type.LOCALNET : Type.TESTNET;
    }

    public ImperativeRadixApi connect(Optional<BasicAuth> basicAuth) {
        return basicAuth
            .map(auth -> ImperativeRadixApi.connect(jsonRpcRootUrl, primaryPort, secondaryPort, auth))
            .orElseGet(() -> ImperativeRadixApi.connect(jsonRpcRootUrl, primaryPort, secondaryPort));
    }

    /**
     * Tries to connect and call the "networkId" method, making sure that we have a working json-rpc api
     *
     * @return the network id
     */
    public int pingJsonRpcApi() {
        try {
            AtomicInteger networkId = new AtomicInteger();
            await().atMost(NETWORK_PING_PATIENCE).pollInterval(Durations.TWO_HUNDRED_MILLISECONDS).
                ignoreException(RadixApiException.class).until(() -> {
                    networkId.set(ImperativeRadixApi.connect(jsonRpcRootUrl, primaryPort, secondaryPort).network().id().getNetworkId());
                    return true;
                });
            return networkId.intValue();
        } catch (ConditionTimeoutException e) {
            throw new RadixApiException(Failure.failure(-1, "Could not get the network's ID within " + NETWORK_PING_PATIENCE));
        }
    }

    public String getJsonRpcRootUrl() {
        return jsonRpcRootUrl;
    }

    public String getFaucetUrl() {
        return faucetUrl;
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
