package com.radixdlt.test.chaos.actions;


import com.radixdlt.test.chaos.ansible.AnsibleImageWrapper;
import com.radixdlt.test.chaos.utils.ChaosExperimentUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ValidatorUnregistrationAction extends ActionWithLikelihood {

    private static final Logger logger = LogManager.getLogger();

    public ValidatorUnregistrationAction(AnsibleImageWrapper ansible, double likelihood) {
        super(ansible, likelihood);
    }

    @Override
    public void setupImplementation() {
        String host = getAnsible().getRandomNodeHost();

        try {
            //httpClient.unregisterValidator(host);
            logger.info("Unregistered node {} as a validator", host);
            ChaosExperimentUtils.annotateGrafana("Unregistered " + host);
        } catch (Exception e) {
            // TODO Sometimes the experiment will try to unregisted a stopped/downed node, which will return a 502
            // this is a hacky way to ignore such error
            e.printStackTrace();
        }
    }

    @Override
    public void teardown() {
        logger.info("Registering all nodes as validators");
        try {
            //getAnsible().getNodeAddressList().forEach(httpClient::registerValidator);
        } catch (Exception e) {
            // TODO ignore failures here too
            e.printStackTrace();
        }
    }

}
