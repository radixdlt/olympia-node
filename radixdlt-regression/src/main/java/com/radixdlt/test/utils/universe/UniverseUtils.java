package com.radixdlt.test.utils.universe;

import org.apache.commons.lang.StringUtils;
import org.radix.GenerateUniverses;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Helps when generating universes and such
 */
public class UniverseUtils {

    private UniverseUtils() {

    }

    /**
     * is probably not thread safe
     */
    public static UniverseVariables generateEnvironmentVariables(int numberOfValidators) {
        // create a custom stream to hold system.out output until the generation finishes.
        // this could be super messy when run concurrently
        var outputAsString = getUniverseGenerationOutputAsString(numberOfValidators);
        var output = outputAsString.split("\\R");
        var keyPairs = IntStream.range(0, numberOfValidators)
            .mapToObj(index -> new ValidatorKeypair()).collect(Collectors.toList());
        var variables = new UniverseVariables();
        variables.setValidatorKeypairs(keyPairs);

        for (String outputLine : output) {
            if (!outputLine.toLowerCase().contains("export")) {
                continue;
            }

            if (outputLine.contains("PUBKEY")) {
                var publicKey = StringUtils.substringAfter(outputLine, "PUBKEY=");
                var validatorNumber = Integer.parseInt(StringUtils.substringBetween(outputLine, "VALIDATOR_", "_PUBKEY"));
                keyPairs.get(validatorNumber).setPublicKey(publicKey);
            } else if (outputLine.contains("PRIVKEY")) {
                var privateKey = StringUtils.substringAfter(outputLine, "PRIVKEY=");
                var validatorNumber = Integer.parseInt(StringUtils.substringBetween(outputLine, "VALIDATOR_", "_PRIVKEY"));
                keyPairs.get(validatorNumber).setPrivateKey(privateKey);
            } else if (outputLine.contains("RADIXDLT_GENESIS_TXN")) {
                var genesisTransaction = StringUtils.substringAfter(outputLine, "RADIXDLT_GENESIS_TXN=");
                variables.setGenesisTransaction(genesisTransaction);
            }

        }

        return variables;
    }

    private static String getUniverseGenerationOutputAsString(int numberOfValidators) {
        var outputStream = new ByteArrayOutputStream();
        var printStream = new PrintStream(outputStream);
        var old = System.out;
        System.setOut(printStream);

        try {
            GenerateUniverses.main(new String[]{"-v", String.valueOf(numberOfValidators)});
            return outputStream.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(old);
        }
    }

}
