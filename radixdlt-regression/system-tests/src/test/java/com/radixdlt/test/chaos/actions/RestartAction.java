package com.radixdlt.test.chaos.actions;

import com.radixdlt.test.chaos.ansible.AnsibleImageWrapper;
import com.radixdlt.test.chaos.utils.ChaosExperimentUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RestartAction extends ActionWithLikelihood {

    private static final Logger logger = LogManager.getLogger();

    public RestartAction(AnsibleImageWrapper ansible, double likelihood) {
        super(ansible, likelihood);
    }

    @Override
    public void setupImplementation() {
        logger.info("Restarting a random node...");
        String randomHost = getAnsible().getRandomNodeHost();
        ChaosExperimentUtils.runCommandOverSsh(randomHost, "docker restart radixdlt_core_1");
        ChaosExperimentUtils.annotateGrafana("docker restart " + randomHost);
    }

}
