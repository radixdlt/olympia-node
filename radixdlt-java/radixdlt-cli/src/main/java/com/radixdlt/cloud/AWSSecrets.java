package com.radixdlt.cloud;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.radixdlt.cli.OutputCapture;
import com.radixdlt.cli.RadixCLI;
import com.radixdlt.utils.AWSSecretManager;
import com.radixdlt.utils.AWSSecretsOutputOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.security.SecureRandom;


public class AWSSecrets {
	private static final Boolean DEFAULT_ENABLE_AWS_SECRETS = false;
	private static final Boolean DEFAULT_RECREATE_AWS_SECRETS = false;
	private static final String DEFAULT_NETWORK_NAME = "testnet";
	private static final String DEFAULT_PREFIX = "fullnode";

	private AWSSecrets() {
	}

	public static void main(String[] args) {
		var options = new Options();
		options.addOption("h", "help", false, "Show usage information (this message)");
		options.addOption("n", "full-node-number", true, "Number of full nodes");
		options.addOption("p", "node-name-prefix", true, "Text prefix with which node name is numbered");
		options.addOption("s", "secret-password-key", true, "Password to encrypt key");
		options.addOption("as", "enable-aws-secrets", true, "Store as AWS Secrets(default: " + DEFAULT_ENABLE_AWS_SECRETS + ")");
		options.addOption("rs", "recreate-aws-secrets", true, "Recreate AWS Secrets(default: " + DEFAULT_RECREATE_AWS_SECRETS + ")");
		options.addOption("k", "network-name", true, "Network name(default: " + DEFAULT_NETWORK_NAME + ")");

		var parser = new DefaultParser();
		try {
			var cmd = parser.parse(options, args);

			if (!cmd.getArgList().isEmpty()) {
				System.err.println("Extra arguments: " + cmd.getArgList().stream().collect(Collectors.joining(" ")));
				usage(options);
				return;
			}

			if (cmd.hasOption('h')) {
				usage(options);
				return;
			}

			var listOfFullNodes = Optional.ofNullable(System.getenv("FULLNODE_NAMES"))
				.map(value -> Stream.of(value.split(","))
					.map(entry -> entry.replaceAll("[^\\w-]", ""))
					.collect(Collectors.toList()))
				.orElse(List.of());

			final int fullNodeCount = getOption(cmd, 'n')
				.map(Integer::parseInt)
				.orElseThrow(() -> new IllegalArgumentException("Must specify number of full nodes"));

			if (fullNodeCount <= 0 && listOfFullNodes.size() <= 0) {
				throw new IllegalArgumentException("There must be at least one full node");
			}

			var networkName = getOption(cmd, 'k').orElse(DEFAULT_NETWORK_NAME);
			var namePrefix = getOption(cmd, 'p').orElse(DEFAULT_PREFIX);
			var defaultKeyPassword = getOption(cmd, 's').orElse("SUEPRSECRET");
			boolean enableAwsSecrets = Boolean.parseBoolean(cmd.getOptionValue("as"));
			boolean recreateAwsSecrets = Boolean.parseBoolean(cmd.getOptionValue("rs"));

			var awsSecretsOutputOptions = new AWSSecretsOutputOptions(enableAwsSecrets, recreateAwsSecrets, networkName);

			var fullnodes = fullNodeCount > 0
							? IntStream.range(0, fullNodeCount)
								.mapToObj(counter -> String.format("%s%s", namePrefix, counter))
								.collect(Collectors.toList())
							: listOfFullNodes;

			for (var nodeName : fullnodes) {
				final var keyStoreName = String.format("%s.ks", nodeName);
				final var passwordName = "password";
				final var keyFileSecretName = String.format("%s/%s/%s", networkName, nodeName, keyStoreName);
				final var passwordSecretName = String.format("%s/%s/%s", networkName, nodeName, passwordName);
				final var password = generatePassword(defaultKeyPassword);
				try (var capture = OutputCapture.startStdout()) {
					var cmdArgs = new String[]{"generate-validator-key", "-k=" + keyStoreName, "-p=" + password};
					System.out.println(Arrays.toString(cmdArgs));
					Security.insertProviderAt(new BouncyCastleProvider(), 1);
					RadixCLI.execute(cmdArgs);

					final var output = capture.stop();
					System.out.println(output);

					if (output.contains("Unable to generate keypair")) {
						throw new Exception(output);
					}

					var keyFilePath = Paths.get(keyStoreName);
					var keyFileAwsSecret = new HashMap<String, Object>();
					try {
						var data = Files.readAllBytes(keyFilePath);
						keyFileAwsSecret.put("key", data);
					} catch (IOException e) {
						throw new IllegalStateException("While reading validator keys", e);
					}
					var keyPasswordAwsSecret = new HashMap<String, Object>();
					keyPasswordAwsSecret.put("key", password);

					writeBinaryAWSSecret(keyFileAwsSecret, keyFileSecretName, awsSecretsOutputOptions, false, true);
					writeBinaryAWSSecret(keyPasswordAwsSecret, passwordSecretName, awsSecretsOutputOptions, false, false);
				} catch (Exception e) {
					System.out.println(e);
				}
			}
		} catch (
			ParseException e) {
			System.out.println(e);
		}
	}

	private static String generatePassword(String password) {
		if (password == null || password.isEmpty() || password.equals("null")) {
			//anphanumeric and special charactrers
			int asciiOrigin = 48;	//0
			int asciiBound = 122;	//z
			int passwordLength = 8;
			SecureRandom random = new SecureRandom();
				return random.ints(asciiOrigin, asciiBound + 1)
					.filter(i -> Character.isAlphabetic(i) || Character.isDigit(i))
					.limit(passwordLength)
					.collect(StringBuilder::new, StringBuilder::appendCodePoint,
							 StringBuilder::append)
					.toString();
		} else {
			return password;
		}
	}
	private static void usage(Options options) {
		new HelpFormatter().printHelp(AWSSecrets.class.getSimpleName(), options, true);
	}

	private static Optional<String> getOption(CommandLine cmd, char opt) {
		return Optional.ofNullable(cmd.getOptionValue(opt));
	}

	private static void writeBinaryAWSSecret(
		Map<String, Object> awsSecret, String secretName, AWSSecretsOutputOptions awsSecretsOutputOptions,
		boolean compress, boolean binarySecret
	) {
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
