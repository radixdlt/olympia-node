package org.radix.universe.output;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class AWSSecretManager {
	private static Region defaultRegion = Region.EU_WEST_2;

	public static void createSecret(String secretName, Object secretValue, String network, Region region, boolean binarySecret) {
		removeBouncyCastleSecurityProvider();
		SecretsManagerClient secretsClient = SecretsManagerClient.builder()
			.region(region)
			.build();

		String secretARN = createNewSecret(secretsClient, secretName, secretValue, network, binarySecret);
		System.out.println("Secret created with ARN " + secretARN);
		secretsClient.close();

	}

	public static void createSecret(String secretName, String secretValue, String network) {
		boolean binarySecret = false;
		createSecret(secretName, secretValue, network, defaultRegion, binarySecret);
	}

	public static void createBinarySecret(String secretName, SdkBytes secretValue, String network) {
		boolean binarySecret = true;
		createSecret(secretName, secretValue, network, defaultRegion, binarySecret);
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
		updateBinarySecret(secretName, secretValue, defaultRegion);
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
		final AWSSecretsUniverseOutput awsSecretsUniverseOutput,
		boolean compress,
		boolean binarySecret
	) {
		ObjectMapper objectMapper = new ObjectMapper();

		try {
			String jsonSecret = objectMapper.writeValueAsString(awsSecret);
			if (compress) {
				byte[] compressedBytes = compressData(jsonSecret);
				createBinarySecret(secretName, SdkBytes.fromByteArray(compressedBytes), awsSecretsUniverseOutput.getNetworkName());
			} else {
				if (binarySecret) {
					createBinarySecret(
						secretName,
						SdkBytes.fromByteArray((byte[]) awsSecret.get("key")),
						awsSecretsUniverseOutput.getNetworkName()
					);
				} else {
					createSecret(secretName, jsonSecret, awsSecretsUniverseOutput.getNetworkName());
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

	public static void updateAWSSecret(
		Map<String, Object> awsSecret,
		String secretName,
		AWSSecretsUniverseOutput awsSecretsUniverseOutput,
		boolean compress,
		boolean binarySecret
	) {
		ObjectMapper objectMapper = new ObjectMapper();
		if (canBeUpdated(awsSecretsUniverseOutput)) {
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
