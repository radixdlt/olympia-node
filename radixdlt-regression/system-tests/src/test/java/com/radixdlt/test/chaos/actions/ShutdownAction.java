package com.radixdlt.test.chaos.actions;

import com.radixdlt.test.chaos.ansible.AnsibleImageWrapper;
import com.radixdlt.test.utils.ChaosExperimentUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ShutdownAction extends ActionWithLikelihood {

    private static final Logger logger = LogManager.getLogger();

    public ShutdownAction(AnsibleImageWrapper ansible, double shutdownLikelihood) {
        super(ansible, shutdownLikelihood);
    }

    @Override
    void setupImplementation() {
        String randomNodeHost = getAnsible().getRandomNodeHost();
        logger.info("Shutting down {}", randomNodeHost);
        ChaosExperimentUtils.runCommandOverSsh(randomNodeHost, "docker stop radixdlt_core_1");
        ChaosExperimentUtils.annotateGrafana("docker stop " + randomNodeHost);
    }

    @Override
    public void teardown() {
        logger.info("Bringing up all nodes...");
        getAnsible().getNodeAddressList().forEach(nodeAddress -> {
            ChaosExperimentUtils.runCommandOverSsh(nodeAddress, "docker start radixdlt_core_1");
        });
    }

}
