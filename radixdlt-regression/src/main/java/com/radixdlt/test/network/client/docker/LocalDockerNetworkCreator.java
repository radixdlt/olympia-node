package com.radixdlt.test.network.client.docker;

import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.test.network.client.RadixHttpClient;
import com.radixdlt.test.utils.TestingUtils;
import com.radixdlt.test.utils.universe.UniverseUtils;
import com.radixdlt.test.utils.universe.UniverseVariables;
import org.apache.commons.compress.utils.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * self-explanatory
 */
public class LocalDockerNetworkCreator {

    private static final Logger logger = LogManager.getLogger();

    public static final int MAX_NUMBER_OF_NODES = 3;

    private LocalDockerNetworkCreator() {

    }

    public static UniverseVariables createNewLocalNetwork(RadixNetworkConfiguration configuration, DockerClient dockerClient) {
        int numberOfNodes = configuration.getDockerConfiguration().getInitialNumberOfNodes();
        logger.info("Initializing new docker network with {} nodes...", numberOfNodes);

        var variables = UniverseUtils.generateEnvironmentVariables(MAX_NUMBER_OF_NODES);

        // network stuff
        var networkName = configuration.getDockerConfiguration().getNetworkName();
        if (!Boolean.parseBoolean(System.getenv("RADIXDLT_DOCKER_DO_NOT_CREATE_NETWORK"))) {
            dockerClient.createNetwork(networkName);
        }

        // starting the network
        IntStream.range(0, numberOfNodes).forEach(nodeNumber -> {
            var dockerContainerName = configuration.getDockerConfiguration().getContainerName();
            var containerName = String.format(dockerContainerName, nodeNumber);
            var privateKey = variables.getValidatorKeypairs().get(nodeNumber).getPrivateKey();
            var primaryPort = configuration.getPrimaryPort();
            var secondaryPort = configuration.getSecondaryPort();
            var remoteSeeds = generateRemoteSeedsConfig(variables, configuration, nodeNumber);

            var environment = createEnvironmentProperties(privateKey, containerName, remoteSeeds,
                variables.getGenesisTransaction(), primaryPort, secondaryPort);
            List<ExposedPort> exposedPorts = Lists.newArrayList();
            var hostConfig = createHostConfigWithPortBindings(nodeNumber, exposedPorts, primaryPort, secondaryPort);

            dockerClient.startNewNode(configuration.getDockerConfiguration().getImage(),
                containerName,
                environment,
                hostConfig,
                exposedPorts,
                networkName
            );
        });

        logger.info("Network started. Waiting for all nodes to be UP...");

        waitUntilNodesAreUp(configuration, MAX_NUMBER_OF_NODES);

        logger.info("All nodes are UP");
        return variables;
    }

    private static void waitUntilNodesAreUp(RadixNetworkConfiguration configuration, int numberOfNodes) {
        var secondaryPort = configuration.getSecondaryPort();
        var httpClient = RadixHttpClient.fromRadixNetworkConfiguration(configuration);

        List<String> rootUrls = IntStream.range(0, numberOfNodes).mapToObj(index -> "http://localhost:" + (secondaryPort + index))
            .collect(Collectors.toList());

        rootUrls.parallelStream().forEach(rootUrl ->
            TestingUtils.waitForNodeToBeUp(httpClient, rootUrl)
        );
    }

    private static String generateRemoteSeedsConfig(UniverseVariables variables, RadixNetworkConfiguration configuration, int nodeNumber) {
        var seeds = IntStream.range(0, variables.getValidatorKeypairs().size()).mapToObj(index -> {
            if (index == nodeNumber) {
                return null;
            }

            var dockerContainerName = configuration.getDockerConfiguration().getContainerName();
            var containerName = String.format(dockerContainerName, index);

            var keyPair = variables.getValidatorKeypairs().get(index);
            return "radix://" + keyPair.getPublicKey() + "@" + containerName;
        }).filter(Objects::nonNull).collect(Collectors.toList());

        return String.join(",", seeds);
    }

    private static List<String> createEnvironmentProperties(String privateKey, String hostIpAddress, String remoteSeeds,
                                                            String genesisTransaction, int primaryPort, int secondaryPort) {
        List<String> env = Lists.newArrayList();

        // java opts
        env.add("JAVA_OPTS=-server -Xmx512m -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -XX:+AlwaysPreTouch -Dguice_bytecode_gen_option=DISABLED "
            + "-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts -Djavax.net.ssl.trustStoreType=jks -Djava.security.egd=file:/dev/urandom "
            + "-Dcom.sun.management.jmxremote.port=9011 -Dcom.sun.management.jmxremote.rmi.port=9011 -Dcom.sun.management.jmxremote.authenticate"
            + "=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost -agentlib:jdwp=transport=dt_socket,"
            + "address=50505,suspend=n,server=y");

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

        // genesis transaction
        env.add("RADIXDLT_GENESIS_TXN=" + genesisTransaction);

        return env;
    }

    private static HostConfig createHostConfigWithPortBindings(int index, List<ExposedPort> exposedPorts, int primaryPort,
                                                               int secondaryPort) {
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

        return HostConfig.newHostConfig().withPortBindings(portBindings).withCapAdd(Capability.NET_ADMIN);
    }

}

