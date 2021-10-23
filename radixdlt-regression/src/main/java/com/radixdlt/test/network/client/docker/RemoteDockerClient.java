package com.radixdlt.test.network.client.docker;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.test.network.SshConfiguration;
import org.apache.commons.lang.StringUtils;
import org.radix.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A class that can run commands (including docker commands e.g. 'docker restart') over ssh
 */
public class RemoteDockerClient implements DockerClient {

    // testing the remote client
    public static void main(String[] args) {
        var configuration = RadixNetworkConfiguration.fromEnv();
        var remoteDockerClient = new RemoteDockerClient(configuration);
        var output = remoteDockerClient.runCommand("1.2.3.4", "pwd");
        System.out.println(output);
    }

    private static Logger logger = LoggerFactory.getLogger(RemoteDockerClient.class);

    private final SshConfiguration sshConfiguration;
    private final String containerName;

    public RemoteDockerClient(RadixNetworkConfiguration configuration) {
        this.sshConfiguration = configuration.getSshConfiguration();
        this.containerName = configuration.getDockerConfiguration().getContainerName();
    }

    public String runCommand(String nodeLocator, String... commands) {
        logger.debug("Run command [{}] at {}", commands, nodeLocator);
        checkProperties();
        var jsch = new JSch();
        Session session = null;
        try {
            jsch.addIdentity(sshConfiguration.getSshKeyLocation(), sshConfiguration.getSshKeyPassphrase());
            session = jsch.getSession(sshConfiguration.getUser(), nodeLocator, sshConfiguration.getPort());
            var config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            logger.debug("Connected over ssh to {}", nodeLocator);

            var channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(commands[0]);
            channel.setInputStream(null);
            ((ChannelExec) channel).setErrStream(System.err);
            channel.connect();
            return IOUtils.toString(channel.getInputStream());
        } catch (JSchException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (session != null) {
                session.disconnect();
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
        // TODO run docker restart over ssh
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void stopNode(String nodeLocator) {
        // TODO run docker stop over ssh
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public void cleanup(String... parameters) {
        // TODO make sure 1) these are nodes and 2) they are brought up again
    }

}
