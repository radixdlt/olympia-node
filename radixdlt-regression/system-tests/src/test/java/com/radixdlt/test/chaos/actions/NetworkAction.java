package com.radixdlt.test.chaos.actions;

import com.radixdlt.test.chaos.ansible.AnsibleImageWrapper;
import com.radixdlt.test.utils.ChaosExperimentUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NetworkAction extends ActionWithLikelihood {

    private static final Logger logger = LogManager.getLogger();

    public NetworkAction(AnsibleImageWrapper ansible, double likelihood) {
        super(ansible, likelihood);
    }

    @Override
    public void setupImplementation() {
        String tcOptions = "loss 3% delay 50ms";
        getAnsible().runPlaybook("slow-down-node.yml", tcOptions, "setup");
        ChaosExperimentUtils.annotateGrafana(tcOptions);
        logger.info("Applied network effects");
    }

    @Override
    public void teardown() {
        logger.info("Removing any network effects...");
        getAnsible().runPlaybook("slow-down-node.yml", "teardown");
    }

}
