package com.radixdlt.test.chaos.actions;

import com.radixdlt.test.chaos.ansible.AnsibleImageWrapper;
import com.radixdlt.test.chaos.utils.ChaosExperimentUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MempoolFillAction extends ActionWithLikelihood {

    private static final Logger logger = LogManager.getLogger();

    private final int durationInSeconds;

    public MempoolFillAction(AnsibleImageWrapper ansible, double likelihood, int durationInSeconds) {
        super(ansible, likelihood);
        this.durationInSeconds = durationInSeconds;
    }

    @Override
    public void setupImplementation() {
        String fillerNode = getAnsible().getRandomNodeHost();
        logger.info("Starting mempool filler from {}", fillerNode);
        ChaosExperimentUtils.annotateGrafana("mempool filler " + fillerNode);

        //httpClient.startMempoolFiller(fillerNode);
        logger.info("Mempool filler started");

        ChaosExperimentUtils.waitSeconds(durationInSeconds);

        //httpClient.stopMempoolFiller(fillerNode);
        logger.info("Mempool filler stopped");
    }

    @Override
    public void teardown() {
        try {
            //getAnsible().getNodeAddressList().forEach(httpClient::stopMempoolFiller);
        } catch (Exception e) {
            //TODO ignore failures when tearing down
            e.printStackTrace();
        }
    }

}
