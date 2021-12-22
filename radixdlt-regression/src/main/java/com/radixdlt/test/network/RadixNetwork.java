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

import com.github.dockerjava.api.exception.DockerClientException;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.test.account.Account;
import com.radixdlt.test.network.client.RadixHttpClient;
import com.radixdlt.test.network.client.docker.DisabledDockerClient;
import com.radixdlt.test.network.client.docker.DockerClient;
import com.radixdlt.test.network.client.docker.DockerNetworkCreator;
import com.radixdlt.test.network.client.docker.LocalDockerClient;
import com.radixdlt.test.network.client.docker.RemoteDockerClient;
import com.radixdlt.test.utils.universe.UniverseVariables;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents an actual radix network with several running nodes. Keeps a list of the nodes, along
 * with their addresses. Has many utility functions. Used when running tests against real networks
 * (acceptance or system tests).
 */
public class RadixNetwork {

  private static final Logger logger = LogManager.getLogger();

  private final RadixNetworkConfiguration configuration;
  private final int networkId;
  private final List<RadixNode> nodes;
  private final DockerClient dockerClient;
  private final RadixHttpClient httpClient;
  private final UniverseVariables universeVariables;

  private RadixNetwork(
      RadixNetworkConfiguration configuration,
      int networkId,
      List<RadixNode> nodes,
      RadixHttpClient httpClient,
      DockerClient dockerClient,
      UniverseVariables universeVariables) {
    this.configuration = configuration;
    this.networkId = networkId;
    this.nodes = nodes;
    this.httpClient = httpClient;
    this.dockerClient = dockerClient;
    this.universeVariables = universeVariables;
  }

  public static RadixNetwork initializeFromEnv() {
    var configuration = RadixNetworkConfiguration.fromEnv();
    prettyPrintConfiguration(configuration);

    // if we are using a local network, we may need to create it and store the universe variables
    UniverseVariables universeVariables = null;
    if (configuration.getDockerConfiguration().shouldInitializeNetwork()
        && configuration.getType() == RadixNetworkConfiguration.Type.LOCALNET) {
      var localDockerClient = new LocalDockerClient(configuration.getDockerConfiguration());
      universeVariables =
          DockerNetworkCreator.createNewLocalNetwork(configuration, localDockerClient);
    }

    var networkId = configuration.pingJsonRpcApi();
    logger.info("Connected to JSON RPC API at {}", configuration.getJsonRpcRootUrl());

    var dockerClient = createDockerClient(configuration);
    var httpClient = RadixHttpClient.fromRadixNetworkConfiguration(configuration);
    var radixNodes = RadixNetworkNodeLocator.locateNodes(configuration, httpClient, dockerClient);
    if (radixNodes == null || radixNodes.size() == 0) {
      throw new RuntimeException("No nodes found, cannot run tests");
    }

    logger.info("Done locating nodes, found {} in total.", radixNodes.size());
    radixNodes.forEach(node -> logger.debug(" * {}", node));
    return new RadixNetwork(
        configuration, networkId, radixNodes, httpClient, dockerClient, universeVariables);
  }

  public Account generateNewAccount() {
    return Account.initialize(configuration);
  }

  /** will return the version from the /version endpoint of the first node (could be any node) */
  public String getVersionOfFirstNode() {
    return httpClient.getVersion(
        nodes.get(0).getRootUrl(), Optional.of(configuration.getSecondaryPort()));
  }

  /** Calls the faucet to send tokens to the given address */
  public String faucet(AccountAddress to) {
    var faucets =
        nodes.stream()
            .filter(node -> node.getAvailableServices().contains(RadixNode.ServiceType.FAUCET))
            .collect(Collectors.toList());
    if (faucets.isEmpty()) {
      throw new FaucetException("No faucet found in this network");
    }
    var nodeWithFaucet = faucets.get(0);
    var address = to.toString(networkId);
    var txID =
        httpClient.callFaucet(
            nodeWithFaucet.getRootUrl(), nodeWithFaucet.getSecondaryPort(), address);
    logger.debug("Faucet successfully sent tokens to {}. TxID: {}", address, txID);
    return txID;
  }

  public DockerClient getDockerClient() {
    return dockerClient;
  }

  public RadixHttpClient getHttpClient() {
    return httpClient;
  }

  public List<RadixNode> getNodes() {
    return nodes;
  }

  public RadixNetworkConfiguration getConfiguration() {
    return configuration;
  }

  public UniverseVariables getUniverseVariables() {
    return universeVariables;
  }

  private static void prettyPrintConfiguration(RadixNetworkConfiguration configuration) {
    logger.debug("Network configuration:");
    logger.debug(
        "JSON-RPC URL: {}, type: {}", configuration.getJsonRpcRootUrl(), configuration.getType());
  }

  private static DockerClient createDockerClient(RadixNetworkConfiguration configuration) {
    var dockerConfiguration = configuration.getDockerConfiguration();
    DockerClient dockerClient;
    try {
      switch (configuration.getType()) {
        case LOCALNET:
          dockerClient = new LocalDockerClient(dockerConfiguration);
          break;
        case TESTNET:
          dockerClient = new RemoteDockerClient(configuration);
          break;
        default:
          dockerClient = new DisabledDockerClient();
      }
      logger.debug("Initialized a {} docker client", configuration.getType());
    } catch (DockerClientException e) {
      logger.warn(
          "Exception {} when trying to initialize a docker client. Client will be disabled.",
          e.getMessage());
      dockerClient = new DisabledDockerClient();
    }
    return dockerClient;
  }
}
