package com.radix.test.network.client;

import com.radix.test.network.TestNetworkConfiguration;
import com.radixdlt.client.lib.api.AccountAddress;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;

/**
 *
 */
public class RadixHttpClient {
    public static RadixHttpClient fromTestNetworkConfiguration(TestNetworkConfiguration configuration) {
        return new RadixHttpClient(configuration.getBasicAuth());
    }

    public RadixHttpClient(String basicAuth) {
        if (basicAuth != null && !basicAuth.isBlank()) {
            var encodedCredentials = Base64.getEncoder().encodeToString(basicAuth.getBytes(StandardCharsets.UTF_8));
            Unirest.config().setDefaultHeader("Authorization", "Basic " + encodedCredentials);
        }
    }

    public JSONObject getNodeInfo(String nodeApiRootUrl) {
        throw new UnsupportedOperationException("Node info is no longer accessible with this client");
    }

    public String callFaucet(String nodeApiRootUrl, AccountAddress address) {
        throw new UnsupportedOperationException("Faucet is no longer accessible with this client");
    }

    public List<URL> getPeers(URL nodeApiRootUrl) {
        throw new UnsupportedOperationException("Peer list is no longer accessible with this client");
    }
}
