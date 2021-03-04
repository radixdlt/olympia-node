package com.radixdlt.test.chaos;

import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.test.chaos.actions.ActionFailedException;
import okhttp3.*;
import org.json.JSONObject;
import org.junit.platform.commons.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * A wrapper around a real httpclient, which provides easy to use methods. Knows the url paths.
 */
public class HttpClient {

    private final static String MEMPOOL_FILLER_PATH = "/api/chaos/mempool-filler";
    private final static String VALIDATOR_REGISTRATION_PATH = "/node/validator";

    private final OkHttpClient okHttpClient;
    private final String protocol;
    private final String faucetUrl;
    private String encodedBasicAuthCredentials;

    public HttpClient() {
        this(true);
    }

    public HttpClient(boolean isHttps) {
        okHttpClient = HttpClients.getSslAllTrustingClient();
        protocol = isHttps ? "https://" : "http://";
        String basicAuthCredentials = System.getenv("HTTP_API_BASIC_AUTH");
        faucetUrl = Optional.ofNullable(System.getenv("FAUCET_URL"))
                .orElse("https://milestonenet-faucet.radixdlt.com/faucet/api/v1/getTokens/");
        if (StringUtils.isNotBlank(basicAuthCredentials)) {
            this.encodedBasicAuthCredentials = Base64.getEncoder()
                    .encodeToString(basicAuthCredentials.getBytes(StandardCharsets.UTF_8));
        }
    }

    public void callFaucetForAddress(String address) {
        try {
            Response response = okHttpClient.newCall(new Request.Builder()
                    .url(faucetUrl + address)
                    .build()
            ).execute();
            if (!response.isSuccessful()) {
                throw new ActionFailedException("Could not call faucet");
            }
        } catch (IOException e) {
            throw new ActionFailedException(e);
        }
    }

    public void unregisterValidator(String host) {
        makeRequest(host, VALIDATOR_REGISTRATION_PATH, "POST", "{\"enabled\":false}");
    }

    public void registerValidator(String host) {
        makeRequest(host, VALIDATOR_REGISTRATION_PATH, "POST", "{\"enabled\":true}");
    }

    public String getMempoolFillerAddress(String host) {
        return makeRequest(host, MEMPOOL_FILLER_PATH, "GET").getString("address");
    }

    public void startMempoolFiller(String host) {
        makeRequest(host, MEMPOOL_FILLER_PATH, "PUT", "{\"enabled\":true}");
    }

    public void stopMempoolFiller(String host) {
        makeRequest(host, MEMPOOL_FILLER_PATH, "PUT", "{\"enabled\":false}");
    }

    private JSONObject makeRequest(String host, String path, String method) {
        return makeRequest(host, path, method, "");
    }

    private JSONObject makeRequest(String host, String path, String method, String body) {
        String stringResponse;
        RequestBody requestBody = StringUtils.isBlank(body) ? null :
                RequestBody.create(MediaType.get("application/json"), body);
        Request request = new Request.Builder()
                .url(protocol + host + path)
                .method(method, requestBody)
                .addHeader("Authorization", "Basic " + encodedBasicAuthCredentials)
                .build();
        try {
            Response response = okHttpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new ActionFailedException(String.format("%s %s%s%s - %d %s",
                        method, protocol, host, path, response.code(), response.message()));
            }
            stringResponse = response.body().string();
        } catch (IOException e) {
            throw new ActionFailedException(e);
        }

        if (StringUtils.isBlank(stringResponse)) {
            return new JSONObject();
        } else {
            return new JSONObject(stringResponse);
        }
    }

}
