package com.radixdlt.test.utils.universe;

import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * holds the output from the universe generation
 */
@Data
@ToString
public class UniverseVariables {

    private List<ValidatorKeypair> validatorKeypairs;
    private String genesisTransaction;

}
