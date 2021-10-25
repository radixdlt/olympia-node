package com.radixdlt.test.network.client;

import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.utils.functional.Failure;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * A small HTTP client that consumes the non-JSON-RPC methods e.g. /health
 * <p>
 * Also consumes the JSON-RPC methods that are not part of the RadixApi client e.g. /faucet
 */
public class RadixHttpClient {

    public enum HealthStatus {
        BOOTING,
        UP
    }

    private static final String FAUCET_PATH = "/faucet";
    private static final String HEALTH_PATH = "/health";
    private static final String METRICS_PATH = "/metrics";

    public static RadixHttpClient fromRadixNetworkConfiguration(RadixNetworkConfiguration configuration) {
        return new RadixHttpClient(configuration.getBasicAuth());
    }

    public RadixHttpClient(String basicAuth) {
        if (basicAuth != null && !basicAuth.isBlank()) {
            var credentials = basicAuth.split("\\:");
            Unirest.config().setDefaultBasicAuth(credentials[0], credentials[1]);
        }
        try {
            var sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, null, new SecureRandom());
            Unirest.config().sslContext(sc);
            Unirest.config().verifySsl(true);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalArgumentException(e); // highly unlikely, algorithm is standard
        }
    }

    public HealthStatus getHealthStatus(String rootUrl) {
        String url = rootUrl + HEALTH_PATH;
        HttpResponse<JsonNode> response = Unirest.get(url).asJson();
        return HealthStatus.valueOf(response.getBody().getObject().getString("status"));
    }

    public Metrics getMetrics(String rootUrl) {
        String url = rootUrl + METRICS_PATH;
        String response = Unirest.get(url).asString().getBody();
        return new Metrics(response);
    }

    public String callFaucet(String rootUrl, int port, String address) {
        var faucetBody = new JSONObject();
        faucetBody.put("jsonrpc", "2.0");
        faucetBody.put("id", "1");
        faucetBody.put("method", "faucet.request_tokens");
        var params = new JSONObject();
        params.put("address", address);
        faucetBody.put("params", params);

        var jsonBodyString = faucetBody.toString(5);
        var response = Unirest.post(rootUrl + ":" + port + FAUCET_PATH).body(jsonBodyString).asJson();
        if (response.isSuccess()) {
            return response.getBody().getObject().getJSONObject("result").getString("txID");
        } else {
            throw new RadixApiException(Failure.failure(response.getStatus(), response.getBody().toString()));
        }
    }

}
