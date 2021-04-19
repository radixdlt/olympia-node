/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.test.chaos.actions;

import com.radixdlt.test.chaos.ansible.AnsibleImageWrapper;
import com.radixdlt.test.chaos.utils.ChaosExperimentUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

public class NetworkAction extends ActionWithLikelihood {

    private static final Logger logger = LogManager.getLogger();

    public NetworkAction(AnsibleImageWrapper ansible, double likelihood) {
        super(ansible, likelihood);
    }

    @Override
    public void setupImplementation() {
        String tcOptions = getRandomLatencyOrLoss();
        getAnsible().runPlaybook("slow-down-node.yml", tcOptions, "setup");
        ChaosExperimentUtils.annotateGrafana(tcOptions);
        logger.info("Applied network effects " + tcOptions);
    }

    @Override
    public void teardown() {
        logger.info("Removing any network effects...");
        getAnsible().runPlaybook("slow-down-node.yml", "teardown");
    }

    private String getRandomLatencyOrLoss() {
        Random random = new Random();
        if (random.nextBoolean()) { // loss
            return "loss " + (random.nextInt(5) + 1) + "%";
        } else { // delay
            return "delay " + (random.nextInt(10) + 1) + "ms";
        }
    }

}
