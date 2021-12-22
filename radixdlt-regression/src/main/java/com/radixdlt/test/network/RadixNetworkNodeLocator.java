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

import com.github.dockerjava.api.exception.DockerException;
import com.google.common.collect.Sets;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.rpc.BasicAuth;
import com.radixdlt.client.lib.api.sync.ImperativeRadixApi;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.client.lib.dto.NetworkPeer;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.test.network.client.RadixHttpClient;
import com.radixdlt.test.network.client.docker.DockerClient;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Has utilities for scanning and locating nodes in radix networks */
public class RadixNetworkNodeLocator {

  private static final Logger logger = LogManager.getLogger();

  private RadixNetworkNodeLocator() {}

  public static List<RadixNode> locateNodes(
      RadixNetworkConfiguration configuration,
      RadixHttpClient httpClient,
      DockerClient dockerClient) {
    List<RadixNode> radixNodes = Lists.newArrayList();
    tryAddFaucetToNodeList(configuration, radixNodes);
    List<NetworkPeer> peers = Lists.newArrayList();
    addSingleNodePeerToList(configuration, radixNodes);

    try {
      if (StringUtils.isBlank(configuration.getBasicAuth())) {
        peers = configuration.connect(Optional.empty()).network().peers();
      } else {
        var credentials = configuration.getBasicAuth().split("\\:");
        var basicAuth = BasicAuth.with(credentials[0], credentials[1]);
        peers = configuration.connect(Optional.of(basicAuth)).network().peers();
      }
    } catch (RadixApiException e) {
      if (e.getMessage().toLowerCase().contains("401 authorization required")) {
        logger.warn(
            "Could not fetch peers list from {} due to 401. The test will use only one archive"
                + " node.",
            configuration.getJsonRpcRootUrl());
        return radixNodes;
      }
    }

    var peersSizePlusOne = peers.size() + 1;
    switch (configuration.getType()) {
      case LOCALNET:
        logger.debug("Searching for {} local nodes...", peersSizePlusOne);
        return locateLocalNodes(configuration, httpClient, dockerClient, peersSizePlusOne);
      case TESTNET:
      default:
        logger.debug("Searching for {} testnet nodes", peersSizePlusOne);
        // eventually, we will have a list of RadixNodes by parsing the peers list
        return radixNodes;
    }
  }

  private static void tryAddFaucetToNodeList(
      RadixNetworkConfiguration configuration, List<RadixNode> radixNodes) {
    if (StringUtils.isNotBlank(configuration.getFaucetUrl())) {
      // these ports might be wrong. Faucet config should be more robust
      Set<RadixNode.ServiceType> availableNodeServices = Sets.newHashSet();
      availableNodeServices.add(RadixNode.ServiceType.FAUCET);
      var faucetNode =
          new RadixNode(
              configuration.getFaucetUrl(),
              configuration.getPrimaryPort(),
              configuration.getSecondaryPort(),
              configuration.getDockerConfiguration().getContainerName(),
              availableNodeServices);
      var rootUrl = configuration.getFaucetUrl();
      var networkId = configuration.connect(Optional.empty()).network().id().getNetworkId();
      var randomAddress =
          AccountAddress.create(ECKeyPair.generateNew().getPublicKey()).toString(networkId);
      try {
        RadixHttpClient.fromRadixNetworkConfiguration(configuration)
            .callFaucet(rootUrl, configuration.getPrimaryPort(), randomAddress);
        radixNodes.add(faucetNode);
        logger.info("Found a primary faucet at {}", rootUrl);
      } catch (FaucetException e) {
        logger.warn(
            "No faucet found at {}, test might fail if it actually requires a faucet", rootUrl);
      }
    }
  }

  private static void addSingleNodePeerToList(
      RadixNetworkConfiguration configuration, List<RadixNode> radixNodes) {
    Set<RadixNode.ServiceType> availableNodeServices = Sets.newHashSet();
    RadixNode singleNode =
        new RadixNode(
            configuration.getJsonRpcRootUrl(),
            configuration.getPrimaryPort(),
            configuration.getSecondaryPort(),
            configuration.getDockerConfiguration().getContainerName(),
            availableNodeServices);
    radixNodes.add(singleNode);
  }

  private static List<RadixNode> locateLocalNodes(
      RadixNetworkConfiguration configuration,
      RadixHttpClient httpClient,
      DockerClient dockerClient,
      int expectedNoOfNodes) {
    var primaryPort = configuration.getPrimaryPort();
    var secondaryPort = configuration.getSecondaryPort();
    var dockerContainerName = configuration.getDockerConfiguration().getContainerName();
    return IntStream.range(0, expectedNoOfNodes)
        .mapToObj(
            counter -> {
              var containerName = String.format(dockerContainerName, counter);
              return figureOutNode(
                  configuration.getJsonRpcRootUrl(),
                  primaryPort + counter,
                  secondaryPort + counter,
                  containerName,
                  httpClient,
                  dockerClient);
            })
        .collect(Collectors.toList());
  }

  /**
   * Tries to figure out which endpoints are available (e.g. /account, /construction) and also tries
   * to use the docker client to establish a connection to this node's container.
   *
   * <p>TODO handle exceptions better. Right now, the test will fail is anything goes wrong here
   */
  private static RadixNode figureOutNode(
      String jsonRpcRootUrl,
      int primaryPort,
      int secondaryPort,
      String expectedContainerName,
      RadixHttpClient httpClient,
      DockerClient dockerClient) {
    Set<RadixNode.ServiceType> availableNodeServices = Sets.newHashSet();

    var api = ImperativeRadixApi.connect(jsonRpcRootUrl, primaryPort, secondaryPort);
    var networkId = api.network().id().getNetworkId();
    availableNodeServices.add(RadixNode.ServiceType.ARCHIVE);

    var randomAddress = AccountAddress.create(ECKeyPair.generateNew().getPublicKey());
    api.account().balances(randomAddress);
    availableNodeServices.add(RadixNode.ServiceType.ACCOUNT);

    try {
      api.transaction().build(TransactionRequest.createBuilder(randomAddress).build());
    } catch (RadixApiException e) {
      // this is expected since the random address has no funds. However, it tells us that
      // /construction is available
      if (!(e.getMessage().contains("Not enough balance"))) {
        throw e;
      }
    }
    availableNodeServices.add(RadixNode.ServiceType.CONSTRUCTION);

    api.api().data();
    availableNodeServices.add(RadixNode.ServiceType.SYSTEM);

    api.local().validatorInfo();
    availableNodeServices.add(RadixNode.ServiceType.VALIDATION);

    httpClient.callFaucet(jsonRpcRootUrl, secondaryPort, randomAddress.toString(networkId));
    availableNodeServices.add(RadixNode.ServiceType.FAUCET);

    // check that the container name is correct.
    try {
      dockerClient.runCommand(expectedContainerName, "pwd");
    } catch (DockerException e) {
      logger.trace(
          "Docker client could not connect due to {} and will be disabled.", e.getMessage());
    }

    return new RadixNode(
        jsonRpcRootUrl, primaryPort, secondaryPort, expectedContainerName, availableNodeServices);
  }
}
