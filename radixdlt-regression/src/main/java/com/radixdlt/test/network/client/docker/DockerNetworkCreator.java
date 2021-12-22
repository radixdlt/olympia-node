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

package com.radixdlt.test.network.client.docker;

import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.test.network.SshConfiguration;
import com.radixdlt.test.network.client.Metrics;
import com.radixdlt.test.network.client.RadixHttpClient;
import com.radixdlt.test.utils.TestingUtils;
import com.radixdlt.test.utils.universe.UniverseUtils;
import com.radixdlt.test.utils.universe.UniverseVariables;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kong.unirest.json.JSONObject;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Durations;

/** Creates radix node networks using docker */
public class DockerNetworkCreator {

  private static final Logger logger = LogManager.getLogger();

  private static final int MAX_NUMBER_OF_NODES = 3;
  private static final String DOCKER_EXEC_COMMAND = "docker exec ";

  private DockerNetworkCreator() {}

  public static UniverseVariables createNewLocalNetwork(
      RadixNetworkConfiguration configuration, LocalDockerClient dockerClient) {
    var numberOfNodes = configuration.getDockerConfiguration().getInitialNumberOfNodes();
    logger.info("Initializing new docker network with {} nodes...", numberOfNodes);
    var variables = UniverseUtils.generateEnvironmentVariables(MAX_NUMBER_OF_NODES);

    // docker network stuff
    var networkName = configuration.getDockerConfiguration().getNetworkName();
    if (!Boolean.parseBoolean(System.getenv("RADIXDLT_DOCKER_DO_NOT_WIPE_NETWORK"))) {
      dockerClient.createNetwork(networkName);
    } else {
      return variables;
    }

    // actually starting the radix network
    IntStream.range(0, numberOfNodes)
        .forEach(
            nodeNumber -> {
              var dockerContainerName = configuration.getDockerConfiguration().getContainerName();
              var containerName = String.format(dockerContainerName, nodeNumber);
              var privateKey = variables.getValidatorKeypairs().get(nodeNumber).getPrivateKey();
              var primaryPort = configuration.getPrimaryPort();
              var secondaryPort = configuration.getSecondaryPort();
              var remoteSeeds =
                  generateSequentialRemoteSeedsConfig(variables, configuration, nodeNumber);

              var environment =
                  createEnvironmentProperties(
                      privateKey,
                      containerName,
                      remoteSeeds,
                      variables.getGenesisTransaction(),
                      primaryPort,
                      secondaryPort);
              List<ExposedPort> exposedPorts = Lists.newArrayList();
              var hostConfig =
                  createHostConfigWithPortBindings(
                      nodeNumber, exposedPorts, primaryPort, secondaryPort);

              dockerClient.startNewNode(
                  configuration.getDockerConfiguration().getImage(),
                  containerName,
                  environment,
                  hostConfig,
                  exposedPorts,
                  networkName);
            });

    logger.info("Network started. Waiting for all nodes to be UP...");
    waitUntilLocalNodesAreUp(configuration, MAX_NUMBER_OF_NODES);
    logger.info("All nodes are UP");
    return variables;
  }

  /**
   * will bring up a fresh new node, without a pre-existing ledger, and sync it to the network
   *
   * @param host the newly initialized node will be hosted here
   */
  public static void initializeAndStartNode(
      RadixNetworkConfiguration configuration,
      String host,
      String genesisTx,
      int networkId,
      String seedsRemote) {
    var dockerClient = new RemoteDockerClient(configuration);
    var dockerConfiguration = configuration.getDockerConfiguration();
    var containerName = dockerConfiguration.getContainerName();
    loginDockerRepository(dockerClient, host, dockerConfiguration.getDockerLogin());

    // prepare env properties
    var universeVariables = UniverseUtils.generateEnvironmentVariables(1);
    var nodePrivateKey = universeVariables.getValidatorKeypairs().get(0).getPrivateKey();
    var env =
        DockerNetworkCreator.createEnvironmentProperties(
            nodePrivateKey, host, seedsRemote, genesisTx, 8080, 3333);
    env.add("RADIXDLT_NETWORK_ID=" + networkId);
    var envString = String.join(" -e ", env);

    // actually start the node
    dockerClient.runCommand(
        host, "docker stop " + containerName + " && docker rm " + containerName);
    var command =
        String.format(
            "docker run --name %s -d -e %s %s",
            containerName, envString, dockerConfiguration.getImage());
    logger.info("Starting node with command [{}]", command);
    var output = dockerClient.runCommand(host, command).replace("\n", "");
    if (StringUtils.isNotBlank(output)) {
      logger.info(output);
    }

    // wait until UP or SYNCING
    await()
        .pollInterval(Durations.FIVE_SECONDS)
        .atMost(Durations.FIVE_MINUTES)
        .until(
            () -> {
              var healthResponse =
                  dockerClient.runCommand(
                      host,
                      DOCKER_EXEC_COMMAND
                          + containerName
                          + " bash -c 'curl -s localhost:3333/health'");
              var status = new JSONObject(healthResponse).getString("status");
              logger.info("Node is {}", status);
              return Objects.equals(status, "UP") || Objects.equals(status, "SYNCING");
            });

    // wait until full ledger sync
    logger.info("Starting sync watch...");
    await()
        .pollInterval(Durations.FIVE_SECONDS)
        .atMost(Duration.ofHours(1))
        .until(
            () -> {
              var metricsString =
                  dockerClient.runCommand(
                      host,
                      DOCKER_EXEC_COMMAND
                          + containerName
                          + " bash -c 'curl -s localhost:3333/metrics'");
              var metrics = new Metrics(metricsString);
              logger.info(
                  "Version {} out of {}. {}% synced.",
                  metrics.getCurrentVersion(),
                  metrics.getTargetVersion(),
                  String.format(
                      "%.2f", 100.0 * metrics.getCurrentVersion() / metrics.getTargetVersion()));
              return metrics.getVersionDiff() == 0L;
            });
    logger.info("Sync complete!");
  }

  /**
   * will bring up a fresh new node, without a pre-existing ledger, and sync it to the network
   *
   * @param host this will host the newly initialized node
   * @param trustedNodeHost the network id, seeds remote and genesis tx will be fetched from here
   */
  public static void initializeAndStartNodeFromTrustedNode(
      RadixNetworkConfiguration configuration, String host, String trustedNodeHost) {
    var dockerConfiguration = configuration.getDockerConfiguration();
    var sshConfiguration = configuration.getSshConfiguration();
    var containerName = dockerConfiguration.getContainerName();

    var trustedContainerName =
        TestingUtils.getEnvWithDefault("RADIXDLT_TRUSTED_DOCKER_CONTAINER_NAME", containerName);
    var trustedSshUser =
        TestingUtils.getEnvWithDefault(
            "RADIXDLT_SYSTEM_TESTING_TRUSTED_SSH_USER",
            configuration.getSshConfiguration().getUser());
    var trustedDockerClient =
        new RemoteDockerClient(
            new SshConfiguration(
                sshConfiguration.getSshKeyLocation(),
                sshConfiguration.getSshKeyPassphrase(),
                trustedSshUser,
                sshConfiguration.getPort()),
            trustedContainerName);
    var genesisTransaction =
        getGenesisTransaction(trustedDockerClient, trustedNodeHost, trustedContainerName);
    var networkId = getNetworkId(trustedDockerClient, trustedNodeHost, trustedContainerName);
    var seedsRemote = getSeedsRemote(trustedDockerClient, trustedNodeHost, trustedContainerName);
    initializeAndStartNode(configuration, host, genesisTransaction, networkId, seedsRemote);
  }

  private static List<String> createEnvironmentProperties(
      String privateKey,
      String hostIpAddress,
      String remoteSeeds,
      String genesisTransaction,
      int primaryPort,
      int secondaryPort) {
    List<String> env = Lists.newArrayList();

    // java opts
    env.add(
        "'JAVA_OPTS=-server -Xmx512m -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -XX:+AlwaysPreTouch"
            + " -Dguice_bytecode_gen_option=DISABLED"
            + " -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
            + " -Djavax.net.ssl.trustStoreType=jks -Djava.security.egd=file:/dev/urandom"
            + " -Dcom.sun.management.jmxremote.port=9011"
            + " -Dcom.sun.management.jmxremote.rmi.port=9011"
            + " -Dcom.sun.management.jmxremote.authenticate=false"
            + " -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost"
            + " -agentlib:jdwp=transport=dt_socket,address=50505,suspend=n,server=y'");

    // per node variables
    env.add("RADIXDLT_NETWORK_SEEDS_REMOTE=" + remoteSeeds);
    env.add("RADIXDLT_NODE_KEY=" + privateKey);
    env.add("RADIXDLT_HOST_IP_ADDRESS=" + hostIpAddress);

    // common variables
    env.add("RADIXDLT_LOG_LEVEL=debug");
    env.add("RADIXDLT_CLIENT_API_PORT=" + primaryPort);
    env.add("RADIXDLT_NODE_API_PORT=" + secondaryPort);
    env.add("RADIXDLT_ENABLE_CLIENT_API=true");
    env.add("RADIXDLT_ARCHIVE_API_ENABLE=true");
    env.add("RADIXDLT_ACCOUNT_API_ENABLE=true");
    env.add("RADIXDLT_METRICS_API_ENABLE=true");
    env.add("RADIXDLT_CONSTRUCT_API_ENABLE=true");
    env.add("RADIXDLT_CHAOS_API_ENABLE=true");
    env.add("RADIXDLT_FAUCET_API_ENABLE=true");
    env.add("RADIXDLT_HEALTH_API_ENABLE=true");
    env.add("RADIXDLT_SYSTEM_API_ENABLE=true");
    env.add("RADIXDLT_VALIDATION_API_ENABLE=true");
    env.add("RADIXDLT_TRANSACTIONS_API_ENABLE=true");
    env.add("RADIXDLT_UNIVERSE_API_ENABLE=true");
    env.add("RADIXDLT_VERSION_API_ENABLE=true");

    // genesis transaction
    env.add("RADIXDLT_GENESIS_TXN=" + genesisTransaction);

    return env;
  }

  private static void waitUntilLocalNodesAreUp(
      RadixNetworkConfiguration configuration, int numberOfNodes) {
    var secondaryPort = configuration.getSecondaryPort();
    var httpClient = RadixHttpClient.fromRadixNetworkConfiguration(configuration);

    var rootUrls =
        IntStream.range(0, numberOfNodes)
            .mapToObj(index -> "http://localhost:" + (secondaryPort + index))
            .collect(Collectors.toList());

    rootUrls.parallelStream()
        .forEach(rootUrl -> TestingUtils.waitForNodeToBeUp(httpClient, rootUrl));
  }

  private static String generateSequentialRemoteSeedsConfig(
      UniverseVariables variables, RadixNetworkConfiguration configuration, int nodeNumber) {
    var seeds =
        IntStream.range(0, variables.getValidatorKeypairs().size())
            .mapToObj(
                index -> {
                  if (index == nodeNumber) {
                    return null;
                  }
                  var dockerContainerName =
                      configuration.getDockerConfiguration().getContainerName();
                  var containerName = String.format(dockerContainerName, index);
                  var keyPair = variables.getValidatorKeypairs().get(index);
                  return "radix://" + keyPair.getPublicKey() + "@" + containerName;
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    return String.join(",", seeds);
  }

  private static HostConfig createHostConfigWithPortBindings(
      int index, List<ExposedPort> exposedPorts, int primaryPort, int secondaryPort) {
    var portBindings = new Ports();

    // primary port (client API)
    int exposedPrimaryPortNumber = primaryPort + index;
    var exposedPrimaryPort = ExposedPort.tcp(primaryPort);
    portBindings.bind(exposedPrimaryPort, Ports.Binding.bindPort(exposedPrimaryPortNumber));
    exposedPorts.add(exposedPrimaryPort);

    // secondary port (node API)
    int exposedSecondaryPortNumber = secondaryPort + index;
    var exposedSecondaryPort = ExposedPort.tcp(secondaryPort);
    portBindings.bind(exposedSecondaryPort, Ports.Binding.bindPort(exposedSecondaryPortNumber));
    exposedPorts.add(exposedSecondaryPort);

    return HostConfig.newHostConfig()
        .withPortBindings(portBindings)
        .withCapAdd(Capability.NET_ADMIN);
  }

  private static String getGenesisTransaction(
      RemoteDockerClient dockerClient, String host, String containerName) {
    logger.debug("Will fetch genesis tx from {} ", host);
    // hardcoded genesis tx file location, might break
    var genesis =
        dockerClient.runCommand(
            host, DOCKER_EXEC_COMMAND + containerName + " bash -c 'cat ./genesis.json'");
    if (StringUtils.isBlank(genesis)) {
      throw new IllegalArgumentException("Genesis tx not found, cannot proceed.");
    }
    var genesisTransactionHex = new JSONObject(genesis).getString("genesis");
    logger.debug("Got genesis tx");
    return genesisTransactionHex;
  }

  private static int getNetworkId(
      RemoteDockerClient dockerClient, String host, String containerName) {
    logger.debug("Will fetch network ID from {} ", host);
    var networkId =
        dockerClient.runCommand(
            host,
            DOCKER_EXEC_COMMAND
                + containerName
                + " bash -c 'env | grep 'NETWORK_ID' | rev | head -c1'");
    logger.debug("Got network ID: {}", networkId);
    try {
      return Integer.parseInt(networkId);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Network ID was not a number: " + networkId);
    }
  }

  private static String getSeedsRemote(
      RemoteDockerClient dockerClient, String host, String containerName) {
    logger.debug("Will fetch remote seeds from {} ", host);
    var seedsRemote =
        dockerClient
            .runCommand(
                host,
                DOCKER_EXEC_COMMAND
                    + containerName
                    + " bash -c 'env | grep SEEDS_REMOTE | cut -f2 -d\"=\"'")
            .replace("\n", "");
    logger.debug("Got seeds remote: {}", seedsRemote);
    return seedsRemote;
  }

  /**
   * Images may need to login a docker repository and this method can run such a command at a host
   */
  private static String loginDockerRepository(
      RemoteDockerClient dockerClient, String host, String dockerLoginCommand) {
    var output = dockerClient.runCommand(host, dockerLoginCommand).replace("\n", "");
    logger.info(output);
    return output;
  }
}
