package com.radixdlt.test.network.client.docker;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.radixdlt.test.network.DockerConfiguration;
import com.radixdlt.test.utils.TestingUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tcp, socket-based client that connects to a local daemon, consuming Docker's Engine API: https://docs.docker.com/engine/api/v1.40/
 */
public class LocalDockerClient implements DockerClient {
    private static Logger logger = LoggerFactory.getLogger(LocalDockerClient.class);

    // the unix default is unix:///var/run/docker.sock. tcp://localhost:2375 might work on windows
    private final String dockerSocketUrl;
    private final String networkName;

    private com.github.dockerjava.api.DockerClient dockerClient;

    public LocalDockerClient(DockerConfiguration dockerConfiguration) {
        this.dockerSocketUrl = dockerConfiguration.getSocketUrl();
        this.networkName = dockerConfiguration.getNetworkName();
        connect();
    }

    public void connect() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(dockerSocketUrl).build();
        var httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig()).build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);
        try {
            dockerClient.pingCmd().exec();
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) { //  this happens when you try to connect to a socket via windows
            throw new DockerClientException("Could not connect to socket " + dockerSocketUrl
                + ". Are you running the tests from windows?");
        }
    }

    @SuppressWarnings("deprecation")
    public String runCommand(String containerId, String... commands) {
        var output = new ByteArrayOutputStream();
        var error = new ByteArrayOutputStream();
        var cmdResponse = dockerClient.execCreateCmd(containerId).withAttachStdout(true)
            .withCmd(commands).exec();
        try {
            dockerClient.execStartCmd(cmdResponse.getId())
                .exec(new ExecStartResultCallback(output, error))
                .awaitCompletion(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new DockerClientException(e.getMessage(), e);
        }
        if (StringUtils.isNotBlank(error.toString())) {
            throw new DockerClientException(error.toString());
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    public void startNewNode(String image, String containerName, List<String> environment, HostConfig hostConfig, List<ExposedPort> exposedPorts,
                             String networkName) {
        var environmentArray = environment.toArray(new String[0]);
        CreateContainerResponse createContainer = dockerClient.createContainerCmd(image)
            .withName(containerName)
            .withEnv(environmentArray)
            .withHostConfig(hostConfig)
            .withExposedPorts(exposedPorts)
            .exec();

        TestingUtils.sleepMillis(500);
        dockerClient.startContainerCmd(createContainer.getId()).exec();
        TestingUtils.sleepMillis(500);
        dockerClient.connectToNetworkCmd().withNetworkId(networkName).withContainerId(containerName).exec();
    }

    public void createNetwork(String networkName) {
        try {
            dockerClient.inspectNetworkCmd().withNetworkId(networkName).exec();
            cleanup();
        } catch (NotFoundException e) {
            // all good, proceed
        } catch (BadRequestException e) { // weird edge case
            cleanup();
        }
        dockerClient.createNetworkCmd().withName(networkName).exec();
    }

    @Override
    public void restartNode(String containerName) {
        dockerClient.restartContainerCmd(containerName).exec();
    }

    @Override
    public void stopNode(String containerName) {
        dockerClient.stopContainerCmd(containerName).exec();
    }

    @Override
    public void cleanup(String... parameters) {
        var count = new AtomicInteger();
        dockerClient.listContainersCmd().withShowAll(true).exec().forEach(container -> {
            if (container.getNetworkSettings().getNetworks().keySet().contains(networkName)) {
                try {
                    dockerClient.stopContainerCmd(container.getId()).exec();
                    dockerClient.removeContainerCmd(container.getId()).exec();
                    logger.debug("Removed container '{}'", container.getId());
                } catch (NotModifiedException e) {
                    // ignore this, this means that the container was already stopped
                }
            }
            count.incrementAndGet();
        });
        dockerClient.removeNetworkCmd(networkName).exec();
        logger.debug("Removed existing network '{}', along with its {} containers", networkName, count.intValue());
    }

}
