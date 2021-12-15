/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
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

package com.radixdlt.test.network.client;

import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.test.network.FaucetException;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.utils.functional.Failure;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;

/**
 * A small HTTP client that consumes the non-JSON-RPC methods e.g. /health
 *
 * <p>Also consumes the JSON-RPC methods that are not part of the RadixApi client e.g. /faucet
 */
public class RadixHttpClient {

  public enum HealthStatus {
    BOOTING,
    SYNCING,
    UP
  }

  private static final String HEALTH_PATH = "/health";
  private static final String METRICS_PATH = "/metrics";
  private static final String VERSION_PATH = "/version";
  private static final String FAUCET_PATH = "/faucet";

  public static RadixHttpClient fromRadixNetworkConfiguration(
      RadixNetworkConfiguration configuration) {
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
    var response = getResponseAsJsonNode(rootUrl + HEALTH_PATH);
    return HealthStatus.valueOf(response.getBody().getObject().getString("status"));
  }

  public Metrics getMetrics(String rootUrl) {
    var url = rootUrl + METRICS_PATH;
    var response = Unirest.get(url).asString().getBody();
    return new Metrics(response);
  }

  /**
   * @param port if empty, will default to whatever port RADIXDLT_JSON_RPC_API_ROOT_URL has (usually
   *     80)
   */
  public String getVersion(String rootUrl, Optional<Integer> port) {
    var url =
        port.isPresent()
            ? String.format("%s:%d%s", rootUrl, port.get(), VERSION_PATH)
            : String.format("%s%s", rootUrl, VERSION_PATH);
    var response = getResponseAsJsonNode(url);
    return response.getBody().getObject().getString("version");
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
      var responseBody = response.getBody().getObject();
      if (responseBody.has("error")) {
        var responseErrorMessage = responseBody.getJSONObject("error").getString("message");
        var errorMessage =
            responseErrorMessage.toLowerCase().contains("not enough balance")
                ? "Faucet is out of tokens!"
                : responseErrorMessage;
        throw new FaucetException(errorMessage);
      }
      return response.getBody().getObject().getJSONObject("result").getString("txID");
    } else {
      var bodyString =
          Objects.isNull(response.getBody())
              ? response.getStatusText() + "(" + response.getStatus() + ")"
              : response.getBody().toString();
      throw new FaucetException(bodyString);
    }
  }

  private HttpResponse<JsonNode> getResponseAsJsonNode(String url) {
    var response = Unirest.get(url).asJson();
    if (response.isSuccess()) {
      return response;
    } else {
      var message =
          response.getBody() == null
              ? "(" + response.getStatus() + ") " + response.getStatusText()
              : response.getBody().getObject().toString();
      throw new RadixApiException(
          Failure.failure(response.getStatus(), "Call to " + url + " failed: " + message));
    }
  }
}
