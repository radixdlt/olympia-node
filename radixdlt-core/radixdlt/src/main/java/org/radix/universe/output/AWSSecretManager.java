package org.radix.universe.output;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AWSSecretManager {
    private static Region defaultRegion = Region.EU_WEST_2;
    public static void createSecret(String secretName, String secretValue, String network, Region region) {
        removeBouncyCastleSecurityProvider();
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
            .region(region)
            .build();

        String secretARN = createNewSecret(secretsClient, secretName, secretValue, network);
        System.out.println("Secret created with ARN " + secretARN);
        secretsClient.close();

    }
    public static void createSecret(String secretName, String secretValue, String network) {
        createSecret(secretName, secretValue, network, defaultRegion);
    }

    public static String getSecret(String secretName, Region region) {
        removeBouncyCastleSecurityProvider();

        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
            .region(region)
            .build();
        String secret = getValue(secretsClient, secretName);
        secretsClient.close();
        return secret;
    }
    public static String getSecret(String secretName) {
        return getSecret(secretName, defaultRegion);
    }

    public static boolean awsSecretExists(String secretName) {
        try {
            getSecret(secretName);
        } catch (SecretsManagerException e) {
            if (e.awsErrorDetails().errorCode().equals("ResourceNotFoundException")) {
                return false;
            } else {
                throw e;
            }
        }
        return true;
    }

    public static void updateSecret(String secretName, String secretValue, Region region) {
        removeBouncyCastleSecurityProvider();
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
            .region(region)
            .build();

        updateMySecret(secretsClient, secretName, secretValue);
        secretsClient.close();
    }
    public static void updateSecret(String secretName, String secretValue) {
        updateSecret(secretName, secretValue, defaultRegion);
    }

    private static void updateMySecret(SecretsManagerClient secretsClient, String secretName, String secretValue) {
        UpdateSecretRequest secretRequest = UpdateSecretRequest.builder()
                .secretId(secretName)
                .secretString(secretValue)
                .build();

        secretsClient.updateSecret(secretRequest);
    }

    private static String getValue(SecretsManagerClient secretsClient, String secretName) {
        GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
        return valueResponse.secretString();

    }

    private static String createNewSecret(SecretsManagerClient secretsClient, String secretName, String secretValue, String network) {
        List<Tag> tagList = buildTags(network, secretName);

        CreateSecretRequest secretRequest = CreateSecretRequest.builder()
                .name(secretName)
                .description("Validator keys")
                .secretString(secretValue)
                .tags(tagList)
                .build();

        CreateSecretResponse secretResponse = secretsClient.createSecret(secretRequest);
        return secretResponse.arn();
    }
    public static void createAWSSecret(
        final Map<String, Object> awsSecret,
        final String secretName,
        final AWSSecretsUniverseOutput awsSecretsUniverseOutput
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonSecret = objectMapper.writeValueAsString(awsSecret);
            AWSSecretManager.createSecret(secretName, jsonSecret, awsSecretsUniverseOutput.getNetworkName());
        } catch (JsonProcessingException e) {
            System.out.println(e);
        } catch (SecretsManagerException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }


    public static void updateAWSSecret(Map<String, Object> awsSecret, String secretName, AWSSecretsUniverseOutput awsSecretsUniverseOutput) {
        ObjectMapper objectMapper = new ObjectMapper();
        if (canBeUpdated(awsSecretsUniverseOutput)) {
            System.out.format("Secret %s exists. And it's going to be replaced %n", secretName);
            try {
                String jsonSecret = objectMapper.writeValueAsString(awsSecret);
                updateSecret(secretName, jsonSecret);
            } catch (JsonProcessingException e) {
                System.out.println(e);
            } catch (SecretsManagerException e) {
                System.err.println(e.awsErrorDetails().errorMessage());
                System.exit(1);
            }
        } else {
            System.out.format("Secret %s exists. It will not be created again %n", secretName);
        }
    }

    private static boolean canBeUpdated(final AWSSecretsUniverseOutput awsSecretsUniverseOutput) {
        return awsSecretsUniverseOutput.getRecreateAwsSecrets()
            && (!awsSecretsUniverseOutput.getNetworkName().equalsIgnoreCase("betanet")
                    || !awsSecretsUniverseOutput.getNetworkName().equalsIgnoreCase("mainnet"));
    }

    //This is needed or the connection to AWS fails with
    /*
        Unable to execute HTTP request: No X509TrustManager implementation available
        software.amazon.awssdk.core.exception.SdkClientException: Unable to execute HTTP request: No X509TrustManager implementation available
     */
    private static void removeBouncyCastleSecurityProvider() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
    }

    private static List<Tag> buildTags(String network, String name) {
        List<Tag> tagList = new ArrayList<>();

        Tag envTypeTag = Tag.builder()
                .key("radixdlt:environment-type")
                .value("development")
                .build();
        Tag teamTag = Tag.builder()
                .key("radixdlt:teamn")
                .value("devops")
                .build();
        Tag appTag = Tag.builder()
                .key("radixdlt:application")
                .value("validator")
                .build();
        Tag nameTag = Tag.builder()
                .key("radixdlt:name")
                .value(name)
                .build();
        Tag networkTag = Tag.builder()
                .key("radixdlt:network")
                .value(network)
                .build();

        tagList.add(envTypeTag);
        tagList.add(appTag);
        tagList.add(teamTag);
        tagList.add(nameTag);
        tagList.add(networkTag);
        return tagList;
    }
}
