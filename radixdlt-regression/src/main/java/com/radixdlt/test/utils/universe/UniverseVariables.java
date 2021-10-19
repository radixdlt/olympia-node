package com.radixdlt.test.utils.universe;

import java.util.List;

/**
 * holds the output from the universe generation
 */
public class UniverseVariables {

    private List<ValidatorKeypair> validatorKeypairs;
    private String genesisTransaction;

    public List<ValidatorKeypair> getValidatorKeypairs() {
        return validatorKeypairs;
    }

    public void setValidatorKeypairs(List<ValidatorKeypair> validatorKeypairs) {
        this.validatorKeypairs = validatorKeypairs;
    }

    public String getGenesisTransaction() {
        return genesisTransaction;
    }

    public void setGenesisTransaction(String genesisTransaction) {
        this.genesisTransaction = genesisTransaction;
    }

}
