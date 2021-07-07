package com.radix.test.network;

import com.radix.test.network.client.HttpException;
import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.api.sync.RadixApi;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Information needed for the initialization of a {@link RadixNetwork}
 */
public final class RadixNetworkConfiguration {

    enum Type {
        LOCALNET,
        TESTNET
    }

    private final String jsonRpcRootUrl;
    private final int primaryPort;
    private final int secondaryPort;
    private final String basicAuth;
    private final Type type;

    private RadixNetworkConfiguration(String jsonRpcRootUrl, int primaryPort, int secondaryPort, String basicAuth, Type type) {
        this.jsonRpcRootUrl = jsonRpcRootUrl;
        this.primaryPort = primaryPort;
        this.secondaryPort = secondaryPort;
        this.basicAuth = basicAuth;
        this.type = type;
    }

    public static RadixNetworkConfiguration fromEnv() {
        try {
            var jsonRpcRootUrlString = getEnvWithDefault("RADIXDLT_JSON_RPC_API_ROOT_URL", "http://localhost");
            var jsonRpcRootUrl = new URL(jsonRpcRootUrlString);
            var primaryPort = (jsonRpcRootUrl.getProtocol().equalsIgnoreCase("https")) ? 443
                : Integer.parseInt(getEnvWithDefault("RADIXDLT_JSON_RPC_API_PRIMARY_PORT", "8080"));
            var secondaryPort = (jsonRpcRootUrl.getProtocol().equalsIgnoreCase("https")) ? 443
                : Integer.parseInt(getEnvWithDefault("RADIXDLT_JSON_RPC_API_SECONDARY_PORT", "3333"));
            var basicAuth = System.getenv("RADIXDLT_BASIC_AUTH");
            var type = determineType(jsonRpcRootUrlString);
            return new RadixNetworkConfiguration(jsonRpcRootUrlString, primaryPort, secondaryPort, basicAuth, type);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Bad JSON-RPC URL", e);
        }
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
     */
    public void pingJsonRpcApi() {
        RadixApi.connect(jsonRpcRootUrl, primaryPort, secondaryPort).flatMap(client -> client.network().id())
            .onFailureDo(() -> {throw new HttpException("Could not connect to JSON-RPC API at " + jsonRpcRootUrl + "");});
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



    private static String getEnvWithDefault(String envName, String defaultValue) {
        String envValue = System.getenv(envName);
        return (envValue == null || envValue.isBlank()) ? defaultValue : envValue;
    }

}
