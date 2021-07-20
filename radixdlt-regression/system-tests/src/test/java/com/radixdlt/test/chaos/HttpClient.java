/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.test.chaos;

import com.radixdlt.client.lib.network.HttpClients;
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
//TODO: replace with implementation based on java client
public class HttpClient {

    private static final String MEMPOOL_FILLER_PATH = "/api/chaos/mempool-filler";
    private static final String NODE_INFO_PATH = "/node";
    private static final String FAUCET_REQUEST_PATH = "/faucet/request";
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
                .orElse("https://rcnet-node0-faucet.radixdlt.com");
        if (StringUtils.isNotBlank(basicAuthCredentials)) {
            this.encodedBasicAuthCredentials = Base64.getEncoder()
                    .encodeToString(basicAuthCredentials.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Will send 10XRD to the given address
     */
    public void callFaucet(String addressToSendTokensTo) {
        String json = "{\"params\":{\"address\":\"" + addressToSendTokensTo + "\"}}";
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
        try (Response response = okHttpClient.newCall(new Request.Builder()
                .url(faucetUrl + FAUCET_REQUEST_PATH)
                .method("POST", body)
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
