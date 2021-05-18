package com.radix.test.network.client;

import com.radix.test.network.TestNetworkConfiguration;
import com.radixdlt.client.lib.api.AccountAddress;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;
import org.apache.commons.compress.utils.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * A thin client which just encapsulates some http calls. It's supposed to consume /node and /system
 */
public class NodeApiClient {

    private static final Logger logger = LogManager.getLogger();

    static {
        Unirest.config().connectTimeout(5000);
        Unirest.config().verifySsl(false);
    }

    private enum Method {
        GET,
        POST
    }

    private static final String NODE_INFO_PATH = "/node";
    private static final String EXECUTE_PATH = "/node/execute";
    private static final String PEERS_PATH = "/system/peers";
    private static final String FAUCET_PATH = "/faucet/request";

    public static NodeApiClient fromTestNetworkConfiguration(TestNetworkConfiguration configuration) {
        return new NodeApiClient(configuration.getBasicAuth());
    }

    public NodeApiClient(String basicAuth) {
        if (basicAuth != null && !basicAuth.isBlank()) {
            var encodedCredentials = Base64.getEncoder().encodeToString(basicAuth.getBytes(StandardCharsets.UTF_8));
            Unirest.config().setDefaultHeader("Authorization", "Basic " + encodedCredentials);
        }
    }

    public JSONObject getNodeInfo(String nodeApiRootUrl) {
        return doGet(nodeApiRootUrl, NODE_INFO_PATH).getBody().getObject();
    }

    public JSONObject transferTokens(String nodeApiRootUrl, AccountAddress to, String tokenRri, long amount) {
        var body = JsonUtils.createTransferTokensBody(to, tokenRri, amount);
        return doPost(nodeApiRootUrl, EXECUTE_PATH, body).getBody().getObject();
    }

    public String callFaucet(String nodeApiRootUrl, AccountAddress address) {
        var addressString = address.toAccountAddress();
        var body = new JSONObject();
        var params = new JSONObject();
        params.put("address", addressString);
        body.put("params", params);
        var response = doPost(nodeApiRootUrl, FAUCET_PATH, body).getBody().getObject();
        if (!response.has("result")) {
            throw new HttpException("Error when calling faucet: " + response);
        }
        return response.getJSONObject("result").getString("transaction_identifier");
    }

    public List<URL> getPeers(URL nodeApiRootUrl) {
        var peers = doGet(nodeApiRootUrl.toExternalForm(), PEERS_PATH).getBody().getArray();
        List<URL> peerUrls = Lists.newArrayList();
        for (int i = 0; i < peers.length(); i++) {
            String endpoint = peers.getJSONObject(i).getString("endpoint");
            String endPointWithoutPort = endpoint.substring(0, endpoint.indexOf(":"));
            try {
                peerUrls.add(new URL(nodeApiRootUrl.getProtocol() + "://" + endPointWithoutPort + ":"
                    + nodeApiRootUrl.getPort()));
            } catch (MalformedURLException e) {
                logger.error("Could not construct url '{}': {}", nodeApiRootUrl.getProtocol() + endpoint,
                    e.getMessage());
            }
        }
        return peerUrls;
    }

    private HttpResponse<JsonNode> doPost(String url, String path, JSONObject body) {
        return doRequest(Method.POST, url, path, body);
    }

    private HttpResponse<JsonNode> doGet(String url, String path) {
        return doRequest(Method.GET, url, path, null);
    }

    private HttpResponse<JsonNode> doRequest(Method method, String url, String path, JSONObject body) {
        HttpResponse<JsonNode> response = null;
        try {
            switch (method) {
                case POST:
                    response = Unirest.post(String.format("%s%s", url, path)).body(body).asJson();
                    break;
                case GET:
                    response = Unirest.get(String.format("%s%s", url, path)).asJson();
                    break;
            }
        } catch (UnirestException e) {
            throw new HttpException(e);
        }

        if (response == null) {
            throw new HttpException("No response received from " + url + path);
        } else if (!response.isSuccess()) {
            throw new HttpException(response.getStatus(), url + path);
        }

        return response;
    }

}
