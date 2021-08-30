package com.radixdlt.test.network.client.docker;

import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.test.utils.universe.UniverseUtils;
import com.radixdlt.test.utils.universe.UniverseVariables;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang.StringUtils;
import org.glassfish.jersey.internal.guava.Joiner;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * self-explanatory
 */
@Log4j2
public class LocalDockerNetworkCreator {

    public static void createNewLocalNetwork(RadixNetworkConfiguration configuration, DockerClient dockerClient) {
        int numberOfNodes = configuration.getDockerConfiguration().getInitialNumberOfNodes();
        log.info("Initializing new docker network with {} nodes...", numberOfNodes);

        var variables = UniverseUtils.generateEnvironmentVariables(3); // the max # of nodes
        var firstNodePublicKey = variables.getValidatorKeypairs().get(0).getPublicKey();

        // network stuff
        String networkName = configuration.getDockerConfiguration().getNetworkName();
        dockerClient.createNetwork(networkName);

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

        log.info("Network initialized");
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
        env.add("JAVA_OPTS=-server -Xmx512m -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -XX:+AlwaysPreTouch -Dguice_bytecode_gen_option=DISABLED " +
            "-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts -Djavax.net.ssl.trustStoreType=jks -Djava.security.egd=file:/dev/urandom " +
            "-Dcom.sun.management.jmxremote.port=9011 -Dcom.sun.management.jmxremote.rmi.port=9011 -Dcom.sun.management.jmxremote.authenticate=false " +
            "-Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=core -agentlib:jdwp=transport=dt_socket,address=50505,suspend=n,server=y");

        // per node variables
        //RADIXDLT_NETWORK_SEEDS_REMOTE: "radix://${RADIXDLT_VALIDATOR_1_PUBKEY}@core1,radix://${RADIXDLT_VALIDATOR_2_PUBKEY}@core2"
        //env.add("RADIXDLT_NETWORK_SEEDS_REMOTE=radix://" + fixedPublicKey + "@docker_test_core0_1");
        //env.add("RADIXDLT_NETWORK_SEEDS_REMOTE=" + remoteSeeds);
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
        Ports portBindings = new Ports();

        // primary port
        int exposedPrimaryPortNumber = primaryPort + index;
        ExposedPort exposedPrimaryPort = ExposedPort.tcp(primaryPort);
        portBindings.bind(exposedPrimaryPort, Ports.Binding.bindPort(exposedPrimaryPortNumber));
        exposedPorts.add(exposedPrimaryPort);

        // secondary port
        int exposedSecondaryPortNumber = secondaryPort + index;
        ExposedPort exposedSecondaryPort = ExposedPort.tcp(secondaryPort);
        portBindings.bind(exposedSecondaryPort, Ports.Binding.bindPort(exposedSecondaryPortNumber));
        exposedPorts.add(exposedSecondaryPort);

        return HostConfig.newHostConfig().withPortBindings(portBindings).withCapAdd(Capability.NET_ADMIN);
    }

}

