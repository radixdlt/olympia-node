package com.radix.test.network;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * TODO
 */
public final class TestNetworkConfiguration {

    enum Type {
        LOCALNET_WITHOUT_DOCKER,
        LOCALNET_DOCKER,
        TESTNET;
    }

    private final URL jsonRpcRootUrl;
    private final URL nodeApiUrl;
    private final String basicAuth;
    private final Type type;

    private TestNetworkConfiguration(URL jsonRpcRootUrl, URL nodeApiUrl, String basicAuth, Type type) {
        this.jsonRpcRootUrl = jsonRpcRootUrl;
        this.nodeApiUrl = nodeApiUrl;
        this.basicAuth = basicAuth;
        this.type = type;
    }


    public static TestNetworkConfiguration fromEnv() {
        try {
            var jsonRpcUrlString = getEnvWithDefault("RADIX_JSON_RPC_ROOT_URL", "http://localhost:8080");
            var jsonRpcUrl = new URL(jsonRpcUrlString);
            var nodeApiUrlString = getEnvWithDefault("RADIX_NODE_API_URL", "http://localhost:3333");
            var nodeApiUrl = new URL(nodeApiUrlString);
            var basicAuth = System.getenv("RADIX_BASIC_AUTH");
            var type = Type.LOCALNET_WITHOUT_DOCKER;
            return new TestNetworkConfiguration(jsonRpcUrl, nodeApiUrl, basicAuth, type);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String getBasicAuth() {
        return basicAuth;
    }

    public URL getJsonRpcRootUrl() {
        return jsonRpcRootUrl;
    }

    public URL getNodeApiRootUrl() {
        return nodeApiUrl;
    }

    public Type getType() {
        return type;
    }

    private static String getEnvWithDefault(String envName, String defaultValue) {
        String envValue = System.getenv(envName);
        return (envValue == null || envValue.isBlank()) ? defaultValue : envValue;
    }

}
