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

package com.radixdlt.test.network;

import static org.awaitility.Awaitility.await;

import com.radixdlt.client.lib.api.rpc.BasicAuth;
import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.test.utils.TestingUtils;
import com.radixdlt.utils.functional.Failure;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Durations;
import org.awaitility.core.ConditionTimeoutException;

/** Information needed for the initialization of a {@link RadixNetwork} */
public class RadixNetworkConfiguration {

  private static final Logger logger = LogManager.getLogger();

  private static final Duration NETWORK_PING_PATIENCE = Durations.ONE_MINUTE;

  public enum Type {
    LOCALNET,
    TESTNET;
  }

  private final String jsonRpcRootUrl;
  private final int primaryPort;
  private final int secondaryPort;
  private final String faucetUrl;
  private final String basicAuth;
  private final Type type;
  private final DockerConfiguration dockerConfiguration;
  private final SshConfiguration sshConfiguration;

  private RadixNetworkConfiguration(
      String jsonRpcRootUrl,
      int primaryPort,
      int secondaryPort,
      String faucetUrl,
      String basicAuth,
      DockerConfiguration dockerConfiguration,
      SshConfiguration sshConfiguration) {
    this.jsonRpcRootUrl = jsonRpcRootUrl;
    this.primaryPort = primaryPort;
    this.secondaryPort = secondaryPort;
    this.faucetUrl = faucetUrl;
    this.basicAuth = basicAuth;
    this.type = determineType(jsonRpcRootUrl);
    this.dockerConfiguration = dockerConfiguration;
    this.sshConfiguration = sshConfiguration;
    if (type != Type.LOCALNET && dockerConfiguration.shouldInitializeNetwork()) {
      logger.warn("Cannot initialize a {} type of network", type);
    }
  }

  public static RadixNetworkConfiguration fromEnv() {
    try {
      var jsonRpcRootUrlString =
          TestingUtils.getEnvWithDefault("RADIXDLT_JSON_RPC_API_ROOT_URL", "http://localhost");
      var jsonRpcRootUrl = new URL(jsonRpcRootUrlString);
      var primaryPort =
          (jsonRpcRootUrl.getProtocol().equalsIgnoreCase("https"))
              ? 443
              : Integer.parseInt(
                  TestingUtils.getEnvWithDefault("RADIXDLT_JSON_RPC_API_PRIMARY_PORT", "8080"));
      var secondaryPort =
          (jsonRpcRootUrl.getProtocol().equalsIgnoreCase("https"))
              ? 443
              : Integer.parseInt(
                  TestingUtils.getEnvWithDefault("RADIXDLT_JSON_RPC_API_SECONDARY_PORT", "3333"));
      var faucetUrl = TestingUtils.getEnvWithDefault("RADIXDLT_FAUCET_URL", "");
      var basicAuth = System.getenv("RADIXDLT_BASIC_AUTH");
      var dockerConfiguration = DockerConfiguration.fromEnv();
      var sshConfiguration = SshConfiguration.fromEnv();
      return new RadixNetworkConfiguration(
          jsonRpcRootUrlString,
          primaryPort,
          secondaryPort,
          faucetUrl,
          basicAuth,
          dockerConfiguration,
          sshConfiguration);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Bad JSON-RPC URL", e);
    }
  }

  public DockerConfiguration getDockerConfiguration() {
    return dockerConfiguration;
  }

  public SshConfiguration getSshConfiguration() {
    return sshConfiguration;
  }

  private static Type determineType(String jsonRpcUrlString) {
    return jsonRpcUrlString.toLowerCase().contains("localhost")
            || jsonRpcUrlString.contains("127.0.0.1")
        ? Type.LOCALNET
        : Type.TESTNET;
  }

  public ImperativeRadixApi connect(Optional<BasicAuth> basicAuth) {
    return basicAuth
        .map(auth -> ImperativeRadixApi.connect(jsonRpcRootUrl, primaryPort, secondaryPort, auth))
        .orElseGet(() -> ImperativeRadixApi.connect(jsonRpcRootUrl, primaryPort, secondaryPort));
  }

  /**
   * Tries to connect and call the "networkId" method, making sure that we have a working json-rpc
   * api
   *
   * @return the network id
   */
  public int pingJsonRpcApi() {
    try {
      AtomicInteger networkId = new AtomicInteger();
      await()
          .atMost(NETWORK_PING_PATIENCE)
          .pollInterval(Durations.TWO_HUNDRED_MILLISECONDS)
          .ignoreException(RadixApiException.class)
          .until(
              () -> {
                networkId.set(
                    ImperativeRadixApi.connect(jsonRpcRootUrl, primaryPort, secondaryPort)
                        .network()
                        .id()
                        .getNetworkId());
                return true;
              });
      return networkId.intValue();
    } catch (ConditionTimeoutException e) {
      throw new RadixApiException(
          Failure.failure(-1, "Could not get the network's ID within " + NETWORK_PING_PATIENCE));
    }
  }

  public String getJsonRpcRootUrl() {
    return jsonRpcRootUrl;
  }

  public String getFaucetUrl() {
    return faucetUrl;
  }

  public int getPrimaryPort() {
    return primaryPort;
  }

  public int getSecondaryPort() {
    return secondaryPort;
  }

  public String getBasicAuth() {
    return basicAuth;
  }

  public Type getType() {
    return type;
  }
}
