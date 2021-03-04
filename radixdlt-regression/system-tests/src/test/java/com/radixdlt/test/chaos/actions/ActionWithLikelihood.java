package com.radixdlt.test.chaos.actions;

import com.radixdlt.test.chaos.ansible.AnsibleImageWrapper;
import com.radixdlt.test.chaos.utils.ChaosExperimentUtils;

/**
 * An action that may or may not happen.
 */
public abstract class ActionWithLikelihood implements Action {

    private final AnsibleImageWrapper ansible;
    private final double likelihood;

    public ActionWithLikelihood(AnsibleImageWrapper ansible, double likelihood) {
        this.ansible = ansible;
        this.likelihood = likelihood;
    }

    /**
     * this is called if the likelihood check succeeded
     */
    abstract void setupImplementation();

    @Override
    public void setup() {
        if (ChaosExperimentUtils.isSmallerThanFractionOfOne(likelihood)) {
            setupImplementation();
        }
    }

    public AnsibleImageWrapper getAnsible() {
        return ansible;
    }

    @Override
    public void teardown() {
        // nothing by default
    }

}
