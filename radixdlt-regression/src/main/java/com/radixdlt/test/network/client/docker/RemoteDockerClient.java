package com.radixdlt.test.network.client.docker;

import com.radixdlt.test.network.RadixNetworkConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tcp, socket-based client that connects to a local daemon, consuming Docker's Engine API: https://docs.docker.com/engine/api/v1.40/
 */
public class RemoteDockerClient implements DockerClient {

    private static Logger logger = LoggerFactory.getLogger(RemoteDockerClient.class);

    private final RadixNetworkConfiguration configuration;

    public RemoteDockerClient(RadixNetworkConfiguration configuration) {
        this.configuration = configuration;
    }

    public void connect() {
        // nothing. can only connect to individual nodes
    }

    public String runCommand(String nodeLocator, String... commands) {
//        JSch jsch = new JSch();
//        try {
//            jsch.addIdentity("", "");
//            Session session = jsch.getSession("root", nodeLocator, 22);
//        } catch (JSchException e) {
//            e.printStackTrace();
//        }
        throw new RuntimeException("Unimplemented");
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

}
