package com.radixdlt.test.network.client.docker;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import org.radix.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A tcp, socket-based client that connects to a local daemon, consuming Docker's Engine API: https://docs.docker.com/engine/api/v1.40/
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

    private final String sshKeyLocation;
    private final String sshKeyPassphrase;
    private final String containerName;

    public RemoteDockerClient(RadixNetworkConfiguration configuration) {
        this.sshKeyLocation = configuration.getSshKeyLocation();
        this.sshKeyPassphrase = configuration.getSshKeyPassphrase();
        this.containerName = configuration.getDockerConfiguration().getContainerName();
    }

    public String runCommand(String nodeLocator, String... commands) {
        logger.debug("Run command [{}] at {}", commands, nodeLocator);
        var jsch = new JSch();
        Session session = null;
        try {
            jsch.addIdentity(sshKeyLocation, sshKeyPassphrase);
            // TODO externalize username and port config once we have a test that uses this
            session = jsch.getSession("todo", nodeLocator, 12345);
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
