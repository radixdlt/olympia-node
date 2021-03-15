/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.test.chaos;

import com.radixdlt.client.core.network.HttpClients;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;
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

    private static final String MEMPOOL_FILLER_PATH = "/api/chaos/mempool-filler";
    private static final String NODE_INFO_PATH = "/node";
    private static final String VALIDATOR_REGISTRATION_PATH = "/node/validator";

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
        try (Response response = okHttpClient.newCall(new Request.Builder()
                .url(faucetUrl + address)
                .build()).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Could not call faucet, request: " + response);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not call faucet");
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

    public String getNodeAddress(String host) {
        return makeRequest(host, NODE_INFO_PATH, "GET").getString("address");
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
        RequestBody requestBody = StringUtils.isBlank(body) ? null
                : RequestBody.create(MediaType.get("application/json"), body);

        Request request = new Request.Builder()
                .url(protocol + host + path)
                .method(method, requestBody)
                .addHeader("Authorization", "Basic " + encodedBasicAuthCredentials)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException(String.format("%s %s%s%s - %d %s",
                        method, protocol, host, path, response.code(), response.message()));
            }
            stringResponse = response.body().string();
            if (StringUtils.isBlank(stringResponse)) {
                return new JSONObject();
            } else {
                return new JSONObject(stringResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}
