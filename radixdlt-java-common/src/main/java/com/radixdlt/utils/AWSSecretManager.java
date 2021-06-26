package com.radixdlt.utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import software.amazon.awssdk.services.secretsmanager.model.Tag;
import software.amazon.awssdk.services.secretsmanager.model.UpdateSecretRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


public class AWSSecretManager {
    private AWSSecretManager() {

    }

    public static void createSecret(String secretName, Object secretValue, String network, Region region, boolean binarySecret) {
        removeBouncyCastleSecurityProvider();
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
            .region(region)
            .build();

        String secretARN = createNewSecret(secretsClient, secretName, secretValue, network, binarySecret);
        System.out.println("Secret created with ARN " + secretARN);
        secretsClient.close();
    }

    public static void createSecret(String secretName, String secretValue, String network, String region) {
        boolean binarySecret = false;
        createSecret(secretName, secretValue, network, Region.of(region), binarySecret);
    }
    public static void createSecret(String secretName, String secretValue, String network) {
        boolean binarySecret = false;
        createSecret(secretName, secretValue, network, getRegion(), binarySecret);
    }

    public static void createBinarySecret(String secretName, SdkBytes secretValue, String network, String region) {
        boolean binarySecret = true;
        createSecret(secretName, secretValue, network, Region.of(region), binarySecret);
    }
    public static void createBinarySecret(String secretName, SdkBytes secretValue, String network) {
        boolean binarySecret = true;
        createSecret(secretName, secretValue, network, getRegion(), binarySecret);
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

    public static void downloadPrivateKey(String secretName, String destFile, Region region) throws IOException {
        System.out.format("About to download private key for %s %n", secretName);
        removeBouncyCastleSecurityProvider();

        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
            .region(region)
            .build();
        SdkBytes secret = getBinaryValue(secretsClient, secretName);
        secretsClient.close();

        uncompressData(secret, destFile);
    }
    public static void downloadPrivateKey(String secretName, String destFile) throws IOException {
        downloadPrivateKey(secretName, destFile, getRegion());
    }

    public static String getSecret(String secretName) {
        return getSecret(secretName, getRegion());
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

    public static void updateBinarySecret(String secretName, SdkBytes secretValue, Region region) {
        removeBouncyCastleSecurityProvider();
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
            .region(region)
            .build();

        updateMyBinarySecret(secretsClient, secretName, secretValue);
        secretsClient.close();
    }

    public static void updateSecret(String secretName, String secretValue, Region region) {
        removeBouncyCastleSecurityProvider();
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
            .region(region)
            .build();

        updateMySecret(secretsClient, secretName, secretValue);
        secretsClient.close();
    }

    public static void updateBinarySecret(String secretName, SdkBytes secretValue) {
        updateBinarySecret(secretName, secretValue, getRegion());
    }

    private static Region getRegion() {
        String regionEnv = Optional.ofNullable((System.getenv("AWS_DEFAULT_REGION"))).orElse("eu-west-2");
        Region region = Region.of(regionEnv);
        return region;
    }

    public static void updateSecret(String secretName, String secretValue) {
        updateSecret(secretName, secretValue, getRegion());
    }

    private static void updateMySecret(SecretsManagerClient secretsClient, String secretName, String secretValue) {
        UpdateSecretRequest secretRequest = UpdateSecretRequest.builder()
            .secretId(secretName)
            .secretString(secretValue)
            .build();

        secretsClient.updateSecret(secretRequest);
    }

    private static void updateMyBinarySecret(SecretsManagerClient secretsClient, String secretName, SdkBytes secretValue) {
        UpdateSecretRequest secretRequest = UpdateSecretRequest.builder()
            .secretId(secretName)
            .secretBinary(secretValue)
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
    private static SdkBytes getBinaryValue(SecretsManagerClient secretsClient, String secretName) {
        GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
            .secretId(secretName)
            .build();

        GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
        return valueResponse.secretBinary();
    }

    private static String createNewSecret(
        SecretsManagerClient secretsClient,
        String secretName,
        Object secretValue,
        String network,
        boolean binarySecret
    ) {
        List<Tag> tagList = buildTags(network, secretName);
        CreateSecretRequest secretRequest;
        if (binarySecret) {
            secretRequest = CreateSecretRequest.builder()
                .name(secretName)
                .description("Validator keys")
                .secretBinary((SdkBytes) secretValue)
                .tags(tagList)
                .build();
        } else {
            secretRequest = CreateSecretRequest.builder()
                .name(secretName)
                .description("Validator keys")
                .secretString((String) secretValue)
                .tags(tagList)
                .build();
        }


        CreateSecretResponse secretResponse = secretsClient.createSecret(secretRequest);
        return secretResponse.arn();
    }

    public static void createAWSSecret(
        final Map<String, Object> awsSecret,
        final String secretName,
        final AWSSecretsOutputOptions awsSecretsOutputOptions,
        boolean compress,
        boolean binarySecret
    ) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            String jsonSecret = objectMapper.writeValueAsString(awsSecret);
            if (compress) {
                byte[] compressedBytes = compressData(jsonSecret);
                createBinarySecret(secretName, SdkBytes.fromByteArray(compressedBytes), awsSecretsOutputOptions.getNetworkName());
            } else {
                if (binarySecret) {
                    createBinarySecret(
                        secretName,
                        SdkBytes.fromByteArray((byte[]) awsSecret.get("key")),
                        awsSecretsOutputOptions.getNetworkName()
                    );
                } else {
                    createSecret(secretName, jsonSecret, awsSecretsOutputOptions.getNetworkName());
                }
            }
        } catch (JsonProcessingException e) {
            System.out.println(e);
        } catch (SecretsManagerException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        } catch (IOException e) {
            System.out.println(e);
            System.exit(1);
        }
    }

    private static byte[] compressData(String data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length());
        GZIPOutputStream gzip = new GZIPOutputStream(bos);
        gzip.write(data.getBytes());
        gzip.close();
        byte[] compressed = bos.toByteArray();
        bos.close();
        return compressed;
    }

    private static void uncompressData(SdkBytes data, String destFile) throws IOException {
        try {

            ByteArrayInputStream bis = new ByteArrayInputStream(data.asByteArray());
            GZIPInputStream gZIPInputStream = new GZIPInputStream(bis);
            FileOutputStream fileOutputStream = new FileOutputStream(destFile);
            int bytesRead;
            byte[] buffer = new byte[1024];
            while ((bytesRead = gZIPInputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
            gZIPInputStream.close();
            fileOutputStream.close();
            System.out.format("Data decompressed successfully %n");

        } catch (IOException ex) {
            ex.printStackTrace();
        }
}

    public static void updateAWSSecret(
        Map<String, Object> awsSecret,
        String secretName,
        AWSSecretsOutputOptions awsSecretsOutputOptions,
        boolean compress,
        boolean binarySecret
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        if (canBeUpdated(awsSecretsOutputOptions)) {
            System.out.format("Secret %s exists. And it's going to be replaced %n", secretName);
            try {
                String jsonSecret = objectMapper.writeValueAsString(awsSecret);
                if (compress) {
                    byte[] compressedBytes = compressData(jsonSecret);
                    updateBinarySecret(secretName, SdkBytes.fromByteArray(compressedBytes));
                } else {
                    if (binarySecret) {
                        updateBinarySecret(secretName, SdkBytes.fromByteArray((byte[]) awsSecret.get("key")));
                    } else {
                        updateSecret(secretName, jsonSecret);
                    }

                }
            } catch (JsonProcessingException e) {
                System.out.println(e);
            } catch (SecretsManagerException e) {
                System.err.println(e.awsErrorDetails().errorMessage());
                System.exit(1);
            } catch (IOException e) {
                System.out.println(e);
                System.exit(1);
            }
        } else {
            System.out.format("Secret %s exists. It will not be created again %n", secretName);
        }
    }

    private static boolean canBeUpdated(final AWSSecretsOutputOptions awsSecretsOutputOptions) {
        return awsSecretsOutputOptions.getRecreateAwsSecrets()
            && (!awsSecretsOutputOptions.getNetworkName().equalsIgnoreCase("betanet")
                    || !awsSecretsOutputOptions.getNetworkName().equalsIgnoreCase("mainnet"));
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
            .key("radixdlt:team")
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
