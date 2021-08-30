package com.radixdlt.test.system;

import com.radixdlt.test.network.RadixNetwork;
import com.radixdlt.test.utils.universe.UniverseUtils;
import com.radixdlt.test.utils.universe.UniverseVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

public class SystemTest {

    private RadixNetwork radixNetwork;
    private UniverseVariables variables;

    @BeforeEach
    public void setup(TestInfo testInfo) {
        radixNetwork = RadixNetwork.initializeFromEnv();
        createDockerNetwork();
    }

    public SystemTest() {

    }

    public void createDockerNetwork() {
        // initialize environment properties for a large number of validators
        this.variables = UniverseUtils.generateEnvironmentVariables(3);

        // start 3 of them
        //DockerClient dockerClient = radixNetwork.getDockerClient();

    }

    public void addNode() {

    }

}
