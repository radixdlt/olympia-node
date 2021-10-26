package com.radixdlt.test.network.client.docker;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.test.network.SshConfiguration;
import com.radixdlt.test.utils.TestingUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A class that can run commands (including docker commands e.g. 'docker restart') over ssh
 */
public class RemoteDockerClient implements DockerClient {

    // testing the remote client
    public static void main(String[] args) {
        var configuration = RadixNetworkConfiguration.fromEnv();
        DockerNetworkCreator.initializeAndStartNodeFromTrustedNode(configuration, "host", "trusted host");
    }

    private static Logger logger = LoggerFactory.getLogger(RemoteDockerClient.class);

    private final SshConfiguration sshConfiguration;
    private final String containerName;

    public RemoteDockerClient(RadixNetworkConfiguration configuration) {
        this(configuration.getSshConfiguration(), configuration.getDockerConfiguration().getContainerName());
    }

    public RemoteDockerClient(SshConfiguration sshConfiguration, String containerName) {
        this.sshConfiguration = sshConfiguration;
        this.containerName = containerName;
    }

    /**
     * this is basically the official example from jsch, copied
     */
    public String runCommand(String nodeLocator, String... commands) {
        logger.debug("Run command [{}] at {}", commands, nodeLocator);
        checkProperties();
        var jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            jsch.addIdentity(sshConfiguration.getSshKeyLocation(), sshConfiguration.getSshKeyPassphrase());
            session = jsch.getSession(sshConfiguration.getUser(), nodeLocator, sshConfiguration.getPort());
            var config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(10000);
            logger.trace("Connected via ssh to {}", nodeLocator);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(String.join(" ", commands));
            var outputBuffer = new ByteArrayOutputStream();
            var errorBuffer = new ByteArrayOutputStream();
            var inputStream = channel.getInputStream();
            var errorStream = channel.getExtInputStream();

            channel.connect();
            byte[] tmp = new byte[1024];
            while (true) {
                while (inputStream.available() > 0) {
                    int i = inputStream.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    outputBuffer.write(tmp, 0, i);
                }
                while (errorStream.available() > 0) {
                    int i = errorStream.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    errorBuffer.write(tmp, 0, i);
                }
                if (channel.isClosed()) {
                    if ((inputStream.available() > 0) || (errorStream.available() > 0)) {
                        continue;
                    }
                    logger.debug("Command exit-status: {}", channel.getExitStatus());
                    break;
                }
                TestingUtils.sleepMillis(250);
            }
            var error = errorBuffer.toString(StandardCharsets.UTF_8);
            var output = outputBuffer.toString(StandardCharsets.UTF_8);
            return (StringUtils.isBlank(output)) ? error : output;
        } catch (IOException | JSchException e) {
            // any error here should be escalated
            throw new RuntimeException(e);
        } finally {
            if (session != null) {
                session.disconnect();
            }
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    private void checkProperties() {
        if (sshConfiguration.getPort() == -1 || StringUtils.isBlank(sshConfiguration.getUser())) {
            throw new IllegalArgumentException("You need to set RADIXDLT_SYSTEM_TESTING_SSH_KEY_USER and "
                + "RADIXDLT_SYSTEM_TESTING_SSH_KEY_PORT to run commands over SSH");
        }
    }

    @Override
    public void restartNode(String nodeLocator) {
        runCommand(nodeLocator, "docker restart " + containerName);
    }

    @Override
    public void stopNode(String nodeLocator) {
        runCommand(nodeLocator, "docker stop " + containerName);
    }

    @Override
    public void cleanup(String... parameters) {
        List.of(parameters).forEach(host -> runCommand(host, "docker start " + containerName));
    }

}
