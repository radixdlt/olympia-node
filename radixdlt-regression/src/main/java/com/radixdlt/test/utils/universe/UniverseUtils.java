package com.radixdlt.test.utils.universe;

import org.apache.commons.lang.StringUtils;
import org.radix.GenerateUniverses;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
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
        String outputAsString = getUniverseGenerationOutputAsString(numberOfValidators);
        String[] output = outputAsString.split("\\R");
        List<ValidatorKeypair> keyPairs = IntStream.range(0, numberOfValidators)
            .mapToObj(index -> new ValidatorKeypair()).collect(Collectors.toList());
        UniverseVariables variables = new UniverseVariables();
        variables.setValidatorKeypairs(keyPairs);

        for (String outputLine : output) {
            if (!outputLine.toLowerCase().contains("export")) {
                continue;
            }

            if (outputLine.contains("PUBKEY")) {
                String publicKey = StringUtils.substringAfter(outputLine, "PUBKEY=");
                int validatorNumber = Integer.parseInt(StringUtils.substringBetween(outputLine, "VALIDATOR_", "_PUBKEY"));
                keyPairs.get(validatorNumber).setPublicKey(publicKey);
            } else if (outputLine.contains("PRIVKEY")) {
                String privateKey = StringUtils.substringAfter(outputLine, "PRIVKEY=");
                int validatorNumber = Integer.parseInt(StringUtils.substringBetween(outputLine, "VALIDATOR_", "_PRIVKEY"));
                keyPairs.get(validatorNumber).setPrivateKey(privateKey);
            } else if (outputLine.contains("RADIXDLT_GENESIS_TXN")) {
                String genesisTransaction = StringUtils.substringAfter(outputLine, "RADIXDLT_GENESIS_TXN=");
                variables.setGenesisTransaction(genesisTransaction);
            }

        }

        return variables;
    }

    private static String getUniverseGenerationOutputAsString(int numberOfValidators) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);
        PrintStream old = System.out;
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
