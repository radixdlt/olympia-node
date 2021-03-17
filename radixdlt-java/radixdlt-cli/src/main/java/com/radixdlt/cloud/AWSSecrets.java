package com.radixdlt.cloud;

import com.radixdlt.cli.OutputCapture;
import com.radixdlt.cli.RadixCLI;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class AWSSecrets {

	private static final Boolean DEFAULT_ENABLE_AWS_SECRETS = false;
	private static final Boolean DEFAULT_RECREATE_AWS_SECRETS = false;
	private static final String DEFAULT_NETWORK_NAME = "testnet";

	public static void main(String[] args) {

		Options options = new Options();
		options.addOption("h", "help", false, "Show usage information (this message)");
		options.addOption("n", "node-name", true, "Name of the node");
		options.addOption("p", "password", true, "Password for the keystore");
		options.addOption("as", "enable-aws-secrets", true, "Store as AWS Secrets(default: " + DEFAULT_ENABLE_AWS_SECRETS + ")");
		options.addOption("rs", "recreate-aws-secrets", true, "Recreate AWS Secrets(default: " + DEFAULT_RECREATE_AWS_SECRETS + ")");
		options.addOption("n", "network-name", true, "Network name(default: " + DEFAULT_NETWORK_NAME + ")");

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

			final String nodeName = Optional.ofNullable(cmd.getOptionValue("n"))
				.orElseThrow(() -> new IllegalArgumentException("Must specify node name"));
			final String password = Optional.ofNullable(cmd.getOptionValue("p"))
				.orElseThrow(() -> new IllegalArgumentException("Must specify password for the store"));

			final String networkName = getOption(cmd, 'n').orElse(DEFAULT_NETWORK_NAME);
			final String keyStoreName = String.format("%1/%2", networkName, nodeName);
			RadixCLI.main(new String[]{"generate-validator-key", "-k=" + keyStoreName, "-n=" + keyStoreName, "-p=nopass"});

		} catch (ParseException e) {
			e.printStackTrace();
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

}
