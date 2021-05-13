package com.radix.test.network.client;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.radix.test.network.TestNetworkConfiguration;
import com.radixdlt.client.lib.api.AccountAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A thin client which just encapsulates some http calls. It's supposed to consume /node and /system
 */
public class NodeApiClient {

    private static final Logger logger = LogManager.getLogger();

    private static final String NODE_INFO_PATH = "/node";
    private static final String FAUCET_PATH = "/faucet/request";

    public static NodeApiClient fromTestNetworkConfiguration(TestNetworkConfiguration configuration) {
        return new NodeApiClient(configuration.getBasicAuth());
    }

    public NodeApiClient(String basicAuth) {
        if (basicAuth != null && basicAuth.isBlank()) {
            String encodedCredentials = Base64.getEncoder().encodeToString(basicAuth.getBytes(StandardCharsets.UTF_8));
            Unirest.setDefaultHeader("Authorization", "Basic " + encodedCredentials);
        }
    }

    public JSONObject getNodeInfo(String nodeApiRootUrl) throws UnirestException {
        HttpResponse<JsonNode> response = Unirest.get(String.format("%s%s", nodeApiRootUrl, NODE_INFO_PATH)).asJson();
        return response.getBody().getObject();
    }

    public JSONObject transferTokens(String nodeApiRootUrl, AccountAddress to, String tokenRri, long amount)
            throws UnirestException {
        JSONObject body = JsonUtils.createTransferTokensBody(to, tokenRri, amount);
        HttpResponse<JsonNode> response = Unirest.post(String.format("%s%s", nodeApiRootUrl, NODE_INFO_PATH))
                .body(body)
                .asJson();
        return response.getBody().getObject();
    }

    public String callFaucet(String nodeApiRootUrl, AccountAddress address) throws UnirestException {
        String addressString = address.toAccountAddress();
        JSONObject body = new JSONObject();
        JSONObject params = new JSONObject();
        params.put("address", addressString);
        body.put("params", params);
        HttpResponse<JsonNode> response = Unirest.post(String.format("%s%s", nodeApiRootUrl, FAUCET_PATH))
                .body(body).asJson();
        JSONObject responseObject = response.getBody().getObject();
        if (!responseObject.has("result")) {
            throw new UnirestException("Error when calling faucet: " + responseObject);
        }
        return responseObject.getJSONObject("result").getString("transaction_identifier");
    }
}
