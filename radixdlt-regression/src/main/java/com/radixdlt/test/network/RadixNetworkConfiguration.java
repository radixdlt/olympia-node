package com.radixdlt.test.network;

import com.radixdlt.client.lib.api.rpc.BasicAuth;
import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.test.utils.TestingUtils;
import org.awaitility.Durations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;

/**
 * Information needed for the initialization of a {@link RadixNetwork}
 */
public class RadixNetworkConfiguration {

    private static Logger logger = LoggerFactory.getLogger(RadixNetworkConfiguration.class);

    public enum Type {
        LOCALNET,
        TESTNET;
    }

    private final String jsonRpcRootUrl;
    private final int primaryPort;
    private final int secondaryPort;
    private final String faucetUrl;
    private final String basicAuth;
    private final String sshKeyLocation;
    private final String sshKeyPassphrase;
    private final Type type;
    private final DockerConfiguration dockerConfiguration;

    private RadixNetworkConfiguration(String jsonRpcRootUrl, int primaryPort, int secondaryPort, String faucetUrl, String basicAuth,
                                      String sshKeyLocation, String sshKeyPassphrase, Type type, DockerConfiguration dockerConfiguration) {
        this.jsonRpcRootUrl = jsonRpcRootUrl;
        this.primaryPort = primaryPort;
        this.secondaryPort = secondaryPort;
        this.faucetUrl = faucetUrl;
        this.basicAuth = basicAuth;
        this.sshKeyLocation = sshKeyLocation;
        this.sshKeyPassphrase = sshKeyPassphrase;
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
            var faucetUrl = TestingUtils.getEnvWithDefault("RADIXDLT_FAUCET_URL", "");
            var basicAuth = System.getenv("RADIXDLT_BASIC_AUTH");
            var sshKeyLocation = TestingUtils.getEnvWithDefault("RADIXDLT_SYSTEM_TESTING_SSH_KEY_LOCATION",
                System.getenv("HOME") + "/.ssh/id_rsa");
            var sshKeyPassphrase = TestingUtils.getEnvWithDefault("RADIXDLT_SYSTEM_TESTING_SSH_KEY_PASSPHRASE", "");
            var type = determineType(jsonRpcRootUrlString);
            var dockerConfiguration = DockerConfiguration.fromEnv();
            if (type != Type.LOCALNET && dockerConfiguration.shouldInitializeNetwork()) {
                logger.warn("Cannot initialize a {} type of network", type);
            }
            return new RadixNetworkConfiguration(jsonRpcRootUrlString, primaryPort, secondaryPort, faucetUrl, basicAuth,
                sshKeyLocation, sshKeyPassphrase, type, dockerConfiguration);
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

    public String getSshKeyLocation() {
        return sshKeyLocation;
    }

    public String getSshKeyPassphrase() {
        return sshKeyPassphrase;
    }

    public Type getType() {
        return type;
    }

}
