package com.radixdlt.test.network.client;

import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.utils.functional.Failure;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;

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

    private HttpClient client;

    public static RadixHttpClient fromRadixNetworkConfiguration(RadixNetworkConfiguration configuration) {
        return new RadixHttpClient(configuration.getBasicAuth());
    }

    public RadixHttpClient(String basicAuth) {
        if (basicAuth != null && !basicAuth.isBlank()) {
            var encodedCredentials = Base64.getEncoder().encodeToString(basicAuth.getBytes(StandardCharsets.UTF_8));
            // TODO use this
        }

        var props = System.getProperties();
        props.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        var trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };

        try {
            var sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            var client = HttpClient.newBuilder()
                .sslContext(sc)
                .build();
            this.client = client;

        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Cannot initialize http client", e);
        }
    }

    public HealthStatus getHealthStatus(String rootUrl) {
        //String url = rootUrl + HEALTH_PATH;
        //HttpResponse<JsonNode> response = Unirest.get(url).asJson();
        //return HealthStatus.valueOf(response.getBody().getObject().getString("network_status"));
        return null;
    }

    public Metrics getMetrics(String rootUrl) {
        //String url = rootUrl + METRICS_PATH;
        //String response = Unirest.get(url).asString().getBody();
        //return new Metrics(response);
        return null;
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
        var request = HttpRequest.newBuilder().uri(URI.create(rootUrl + ":" + port + FAUCET_PATH)).method(
            "POST", HttpRequest.BodyPublishers.ofString(jsonBodyString)).build();

        try {
            var httpResponse = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            int status = httpResponse.statusCode();
            if (status >= 200 && status < 300) {
                JSONObject response = new JSONObject(httpResponse.body());
                return response.getJSONObject("result").getString("txID");
            } else {
                throw new RadixApiException(Failure.failure(-1, "Faucet call failed: (" + status + "): "
                    + httpResponse.body()));
            }
        } catch (IOException | InterruptedException e) {
            throw new RadixApiException(Failure.failure(-1, e.getMessage()));
        }
    }

}
