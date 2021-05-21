package com.radix.test.docker;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * A tcp, socket-based client that consumes Docker's Engine API: https://docs.docker.com/engine/api/v1.40/
 */
public class DockerClient {

    private static Logger logger = LoggerFactory.getLogger(DockerClient.class);

    // the unix default is unix:///var/run/docker.sock. tcp://localhost:2375 might work on windows
    private String dockerSocketUrl;

    private com.github.dockerjava.api.DockerClient dockerClient;

    /**
     * will try to connect to the docker daemon, throwing a {@link com.github.dockerjava.api.exception.DockerException}
     * on failure
     */
    public void connect() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(dockerSocketUrl).build();
        var httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig()).build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);
        dockerClient.pingCmd().exec();
    }

    public String runShellCommandAndGetOutput(String containerId, String... commandArray) {
        var output = new ByteArrayOutputStream();
        var error = new ByteArrayOutputStream();
        var cmdResponse = dockerClient.execCreateCmd(containerId).withAttachStdout(true)
            .withCmd(commandArray).exec();
        try {
            dockerClient.execStartCmd(cmdResponse.getId())
                .exec(new ExecStartResultCallback(output, error)).awaitCompletion(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e); // TODO don't just throw Runtimes
        }
        if (StringUtils.isNotBlank(error.toString())) {
            throw new RuntimeException(error.toString());
        }
        return output.toString(StandardCharsets.UTF_8);
    }

}
