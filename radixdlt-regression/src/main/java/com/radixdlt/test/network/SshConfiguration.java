package com.radixdlt.test.network;

import com.radixdlt.test.utils.TestingUtils;

/**
 * properties used to connect to testnet nodes via SSH
 */
public class SshConfiguration {

    private final String sshKeyLocation;
    private final String sshKeyPassphrase;
    private final String user;
    private final int port;

    public static SshConfiguration fromEnv() {
        var sshKeyLocation = TestingUtils.getEnvWithDefault("RADIXDLT_SYSTEM_TESTING_SSH_KEY_LOCATION",
            System.getenv("HOME") + "/.ssh/id_rsa");
        var sshKeyPassphrase = TestingUtils.getEnvWithDefault("RADIXDLT_SYSTEM_TESTING_SSH_KEY_PASSPHRASE", "");
        var sshUser = TestingUtils.getEnvWithDefault("RADIXDLT_SYSTEM_TESTING_SSH_USER", "");
        var sshPort = TestingUtils.getEnvWithDefault("RADIXDLT_SYSTEM_TESTING_SSH_PORT", -1);
        return new SshConfiguration(sshKeyLocation, sshKeyPassphrase, sshUser, sshPort);
    }

    public SshConfiguration(String sshKeyLocation, String sshKeyPassphrase, String user, int port) {
        this.sshKeyLocation = sshKeyLocation;
        this.sshKeyPassphrase = sshKeyPassphrase;
        this.user = user;
        this.port = port;
    }

    public String getSshKeyLocation() {
        return sshKeyLocation;
    }

    public String getSshKeyPassphrase() {
        return sshKeyPassphrase;
    }

    public String getUser() {
        return user;
    }

    public int getPort() {
        return port;
    }

}
