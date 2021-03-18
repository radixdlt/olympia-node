package com.radixdlt.cloud;

import com.radixdlt.cli.OutputCapture;
import com.radixdlt.cli.RadixCLI;
import com.radixdlt.utils.AWSSecretManager;
import com.radixdlt.utils.AWSSecretsOutputOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;


public class AWSSecrets {

	private static final Boolean DEFAULT_ENABLE_AWS_SECRETS = false;
	private static final Boolean DEFAULT_RECREATE_AWS_SECRETS = false;
	private static final String DEFAULT_NETWORK_NAME = "testnet";

	private AWSSecrets() {
	}

	public static void main(String[] args) {

		Options options = new Options();
		options.addOption("h", "help", false, "Show usage information (this message)");
		options.addOption("n", "full-node-number", true, "Number of full nodes");
		options.addOption("as", "enable-aws-secrets", true, "Store as AWS Secrets(default: " + DEFAULT_ENABLE_AWS_SECRETS + ")");
		options.addOption("rs", "recreate-aws-secrets", true, "Recreate AWS Secrets(default: " + DEFAULT_RECREATE_AWS_SECRETS + ")");
		options.addOption("k", "network-name", true, "Network name(default: " + DEFAULT_NETWORK_NAME + ")");

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cmd = parser.parse(options, args);
			if (!cmd.getArgList().isEmpty()) {
				System.err.println("Extra arguments: " + cmd.getArgList().stream().collect(Collectors.joining(" ")));
				usage(options);
				return;
			}

			if (cmd.hasOption('h')) {
				usage(options);
				return;
			}

			final int fullNodeCount = Integer
				.parseInt(getOption(cmd, 'n').orElseThrow(() -> new IllegalArgumentException("Must specify number of full nodes")));
			if (fullNodeCount <= 0) {
				throw new IllegalArgumentException("There must be at least one full node");
			}

			final String networkName = getOption(cmd, 'k').orElse(DEFAULT_NETWORK_NAME);
			final boolean enableAwsSecrets = Boolean.parseBoolean(cmd.getOptionValue("as"));
			final boolean recreateAwsSecrets = Boolean.parseBoolean(cmd.getOptionValue("rs"));

			final AWSSecretsOutputOptions awsSecretsOutputOptions = new AWSSecretsOutputOptions(
				enableAwsSecrets, recreateAwsSecrets, networkName);
			IntStream.range(0, fullNodeCount).forEach(i -> {
				final String nodeName = String.format("fullnode%s", i);
				final String keyStoreName = String.format("%s.ks", nodeName);
				final String passwordName = "password";
				final String keyFileSecretName = String.format("%s/%s/%s", networkName, nodeName, keyStoreName);
				final String passwordSecretName = String.format("%s/%s/%s", networkName, nodeName, passwordName);
				final String password = passwordName;
				try (OutputCapture capture = OutputCapture.startStdout()) {

					String[] cmdArgs = {"generate-validator-key", "-k=" + keyStoreName, "-n=" + keyStoreName, "-p=" + password};
					System.out.println(java.util.Arrays.toString(cmdArgs));
					Security.insertProviderAt(new BouncyCastleProvider(), 1);
					RadixCLI.execute(cmdArgs);
					final String output = capture.stop();
					System.out.println(output.toString());
					if (output.contains("Unable to generate keypair")) {
						throw new Exception(output.toString());
					}
					Path keyFilePath = Paths.get(keyStoreName);
					Map<String, Object> keyFileAwsSecret = new HashMap<>();
					try {

						byte[] data = Files.readAllBytes(keyFilePath);
						keyFileAwsSecret.put("key", data);
					} catch (IOException e) {
						throw new IllegalStateException("While reading validator keys", e);
					}
					Map<String, Object> keyPasswordAwsSecret = new HashMap<>();
					keyPasswordAwsSecret.put("key", password);

					writeBinaryAWSSecret(keyFileAwsSecret, keyFileSecretName, awsSecretsOutputOptions, false, true);
					writeBinaryAWSSecret(keyPasswordAwsSecret, passwordSecretName, awsSecretsOutputOptions, false, false);
				} catch (Exception e) {
					System.out.println(e);
				}
			});
		} catch (ParseException e) {
			System.out.println(e);
		}
	}

	private static void usage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(AWSSecrets.class.getSimpleName(), options, true);
	}

	private static Optional<String> getOption(CommandLine cmd, char opt) {
		String value = cmd.getOptionValue(opt);
		return Optional.ofNullable(value);
	}

	private static void writeBinaryAWSSecret(Map<String, Object> awsSecret, String secretName, AWSSecretsOutputOptions awsSecretsOutputOptions,
		boolean compress, boolean binarySecret) {
		if (!awsSecretsOutputOptions.getEnableAwsSecrets()) {
			System.out.println("Secret " + secretName + " not stored in AWS");
			return;
		}
		if (AWSSecretManager.awsSecretExists(secretName)) {
			AWSSecretManager.updateAWSSecret(awsSecret, secretName, awsSecretsOutputOptions, compress, binarySecret);
		} else {
			AWSSecretManager.createAWSSecret(awsSecret, secretName, awsSecretsOutputOptions, compress, binarySecret);
		}
	}
}
