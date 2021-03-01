package org.radix.universe.output;

import com.radixdlt.utils.Bytes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.security.Security;

public class AWSSecretManager {
    public static void createSecret(String secretName, String secretValue) {
        removeBouncyCastleSecurityProvider();

        Region region = Region.EU_WEST_2;
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                .region(region)
                .build();

        String secretARN = createNewSecret(secretsClient, secretName, secretValue);
        System.out.println("Secret created with ARN " + secretARN);
        secretsClient.close();
    }

    public static String getSecret(String secretName) {
        removeBouncyCastleSecurityProvider();

        Region region = Region.EU_WEST_2;
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                .region(region)
                .build();
        String secret = getValue(secretsClient, secretName);
        secretsClient.close();
        return secret;
    }

    public static boolean checkIfSecretsExist(String secretName) {
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

    public static void updateSecret(String secretName, String secretValue) {
        removeBouncyCastleSecurityProvider();
        Region region = Region.EU_WEST_2;
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                .region(region)
                .build();

        updateMySecret(secretsClient, secretName, secretValue);
        secretsClient.close();
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

    private static String createNewSecret(SecretsManagerClient secretsClient, String secretName, String secretValue) {
        CreateSecretRequest secretRequest = CreateSecretRequest.builder()
                .name(secretName)
                .description("Validator keys")
                .secretString(secretValue)
                .build();

        CreateSecretResponse secretResponse = secretsClient.createSecret(secretRequest);
        return secretResponse.arn();
    }

    //This is needed or the connection to AWS fails with
    /*
        Unable to execute HTTP request: No X509TrustManager implementation available
        software.amazon.awssdk.core.exception.SdkClientException: Unable to execute HTTP request: No X509TrustManager implementation available
     */
    private static void removeBouncyCastleSecurityProvider() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
    }
}
