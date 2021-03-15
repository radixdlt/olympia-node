package com.radixdlt.test.chaos.actions;

import com.radixdlt.test.chaos.HttpClient;
import com.radixdlt.test.chaos.ansible.AnsibleImageWrapper;
import com.radixdlt.test.chaos.utils.ChaosExperimentUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ValidatorRegistrationAction extends ActionWithLikelihood {

    private static final Logger logger = LogManager.getLogger();

    private final HttpClient httpClient;

    public ValidatorRegistrationAction(AnsibleImageWrapper ansible, double likelihood) {
        super(ansible, likelihood);
        httpClient = new HttpClient();
    }

    @Override
    public void setupImplementation() {
        String host = getAnsible().getRandomNodeHost();

        String address = httpClient.getNodeAddress(host);
        httpClient.callFaucetForAddress(address);
        logger.info("Got tokens");
        ChaosExperimentUtils.waitSeconds(5);

        httpClient.unregisterValidator(host);
        logger.info("Unregistered node {} as a validator", host);
        ChaosExperimentUtils.annotateGrafana("Unregistered " + host);
    }

    @Override
    public void teardown() {
        logger.info("Registering all nodes as validators");
        try {
            getAnsible().getNodeAddressList().forEach(httpClient::registerValidator);
        } catch (Exception e) {
            // TODO ignore failures here too
            e.printStackTrace();
        }
    }

}
