package com.radix.test.network;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Information needed for the location the nodes of a {@link TestNetwork}
 */
public final class TestNetworkConfiguration {

    enum Type {
        LOCALNET,
        TESTNET
    }

    private final URL jsonRpcRootUrl;
    private final URL nodeApiRootUrl;
    private final String basicAuth;
    private final Type type;

    private TestNetworkConfiguration(URL jsonRpcRootUrl, URL nodeApiRootUrl, String basicAuth, Type type) {
        this.jsonRpcRootUrl = jsonRpcRootUrl;
        this.nodeApiRootUrl = nodeApiRootUrl;
        this.basicAuth = basicAuth;
        this.type = type;
    }

    public static TestNetworkConfiguration fromEnv() {
        try {
            var jsonRpcRootUrlString = getEnvWithDefault("RADIX_JSON_RPC_ROOT_URL", "http://localhost:8080");
            var jsonRpcRootUrl = new URL(jsonRpcRootUrlString);
            var nodeApiPort = (jsonRpcRootUrl.getProtocol().equalsIgnoreCase("https")) ? 443
                : Integer.parseInt(getEnvWithDefault("RADIX_NODE_API_PORT", "3333"));
            var nodeApiRootUrl = new URL(String.format("%s://%s%s:%s", jsonRpcRootUrl.getProtocol(),
                jsonRpcRootUrl.getHost(), jsonRpcRootUrl.getPath(), nodeApiPort));
            var basicAuth = System.getenv("RADIX_BASIC_AUTH");
            var type = determineType(jsonRpcRootUrlString);
            return new TestNetworkConfiguration(jsonRpcRootUrl, nodeApiRootUrl, basicAuth, type);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Type determineType(String jsonRpcUrlString) {
        return jsonRpcUrlString.toLowerCase().contains("localhost") || jsonRpcUrlString.contains("127.0.0.1")
            ? Type.LOCALNET : Type.TESTNET;
    }

    public String getBasicAuth() {
        return basicAuth;
    }

    public URL getJsonRpcRootUrl() {
        return jsonRpcRootUrl;
    }

    public URL getNodeApiRootUrl() {
        return nodeApiRootUrl;
    }

    public Type getType() {
        return type;
    }

    private static String getEnvWithDefault(String envName, String defaultValue) {
        String envValue = System.getenv(envName);
        return (envValue == null || envValue.isBlank()) ? defaultValue : envValue;
    }

}
