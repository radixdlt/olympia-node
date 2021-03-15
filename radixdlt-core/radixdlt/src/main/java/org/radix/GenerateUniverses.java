/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.radixdlt.CryptoModule;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.ClientAtom;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.radixdlt.crypto.exception.CryptoException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;

import com.radixdlt.statecomputer.checkpoint.RadixNativeTokenModule;
import com.radixdlt.universe.DevUniverseConfigModule;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.GenesisAtomProvider;
import com.radixdlt.universe.ProductionUniverseConfigModule;
import com.radixdlt.universe.TestUniverseConfigModule;
import com.radixdlt.universe.UniverseConfig;
import com.radixdlt.universe.UniverseConfiguration;
import org.apache.logging.log4j.util.Strings;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.keys.Keys;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt256s;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.radix.universe.output.AWSSecretManager;
import org.radix.universe.output.AWSSecretsUniverseOutput;
import org.radix.universe.output.HelmUniverseOutput;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.Security;
import java.time.Instant;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class GenerateUniverses {
	private GenerateUniverses() { }

	private static final BigDecimal SUB_UNITS_BIG_DECIMAL
		= new BigDecimal(UInt256s.toBigInteger(TokenDefinitionUtils.SUB_UNITS));
	private static final String DEFAULT_UNIVERSE = UniverseType.DEVELOPMENT.toString().toLowerCase();
	private static final String DEFAULT_TIMESTAMP = String.valueOf(Instant.parse("2020-01-01T00:00:00.00Z").toEpochMilli());
	private static final String DEFAULT_KEYSTORE = "universe.ks";
	private static final String DEFAULT_STAKE = "5000000";
	private static final String VALIDATOR_TEMPLATE = "validator%s.ks";
	private static final String STAKER_TEMPLATE = "staker%s.ks";
	private static final String DEFAULT_HELM_VALUES_OUTPUT_DIRECTORY = ".";
	private static final Boolean DEFAULT_HELM_OUTPUT = false;
	private static final Boolean DEFAULT_ENABLE_AWS_SECRETS = false;
	private static final Boolean DEFAULT_RECREATE_AWS_SECRETS = false;
	private static final String DEFAULT_NETWORK_NAME = "testnet";
	private static final BigDecimal DEFAULT_ISSUANCE = BigDecimal.valueOf(100_000_000);

	public static void main(String[] args) {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);

		Options options = new Options();
		options.addOption("h", "help",                   false, "Show usage information (this message)");
		options.addOption("c", "no-cbor-output",         false, "Suppress DSON output");
		options.addOption("i", "issue-default-tokens",   false, "Issue tokens to default keys 1, 2, 3, 4 and 5 (dev universe only)");
		options.addOption("j", "no-json-output",         false, "Suppress JSON output");
		options.addOption("k", "keystore",               true,  "Specify universe keystore (default: " + DEFAULT_KEYSTORE + ")");
		options.addOption("p", "include-private-keys",   false, "Include universe, validator and staking private keys in output");
		options.addOption("S", "stake-amounts",          true,  "Amount of stake for each staked node (default: " + DEFAULT_STAKE + ")");
		options.addOption("t", "universe-type",          true,  "Specify universe type (default: " + DEFAULT_UNIVERSE + ")");
		options.addOption("T", "universe-timestamp",     true,  "Specify universe timestamp (default: " + DEFAULT_TIMESTAMP + ")");
		options.addOption("v", "validator-count",        true,  "Specify number of validators to generate (required)");
		options.addOption("hc", "helm-configuration", true, "Generates Helm values for validators(default: " + DEFAULT_HELM_OUTPUT + ")");
		options.addOption("d", "helm-output-directory", true, "Output dir to add Helm values files(default: " + DEFAULT_HELM_VALUES_OUTPUT_DIRECTORY + ")");
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

			final boolean suppressCborOutput = cmd.hasOption('c');
			final boolean suppressJsonOutput = cmd.hasOption('j');
			final String universeKeyFile = getOption(cmd, 'k').orElse(DEFAULT_KEYSTORE);
			final boolean outputPrivateKeys = cmd.hasOption('p');

			final boolean outputHelmValues = Boolean.parseBoolean(cmd.getOptionValue("hc"));
			final String helmValuesPath = getOption(cmd, 'd').orElse(DEFAULT_HELM_VALUES_OUTPUT_DIRECTORY);
			final HelmUniverseOutput helmUniverseOutput = new HelmUniverseOutput(outputHelmValues, helmValuesPath);
			final boolean enableAwsSecrets = Boolean.parseBoolean(cmd.getOptionValue("as"));
			final boolean recreateAwsSecrets = Boolean.parseBoolean(cmd.getOptionValue("rs"));
			final String networkName = getOption(cmd, 'n').orElse(DEFAULT_NETWORK_NAME);
			final AWSSecretsUniverseOutput awsSecretsUniverseOutput = new AWSSecretsUniverseOutput(enableAwsSecrets, recreateAwsSecrets, networkName);

			final ImmutableList<UInt256> stakes = parseStake(getOption(cmd, 'S').orElse(DEFAULT_STAKE));
			final UniverseType universeType = parseUniverseType(getOption(cmd, 't').orElse(DEFAULT_UNIVERSE));
			final long universeTimestampSeconds = Long.parseLong(getOption(cmd, 'T').orElse(DEFAULT_TIMESTAMP));
			final int validatorsCount = Integer.parseInt(
				getOption(cmd, 'v').orElseThrow(() -> new IllegalArgumentException("Must specify number of validators"))
			);

			if (stakes.isEmpty()) {
				throw new IllegalArgumentException("Must specify at least one staking amount");
			}
			if (validatorsCount <= 0) {
				throw new IllegalArgumentException("There must be at least one validator");
			}

			final ImmutableList<ECKeyPair> validatorKeys = getValidatorKeys(validatorsCount);
			final ImmutableList<StakeDelegation> stakeDelegations = getStakeDelegation(
				Lists.transform(validatorKeys, ECKeyPair::getPublicKey), stakes
			);

			final ImmutableList.Builder<TokenIssuance> tokenIssuancesBuilder = ImmutableList.builder();
			if (universeType == UniverseType.DEVELOPMENT && cmd.hasOption("i")) {
				tokenIssuancesBuilder.add(
					TokenIssuance.of(pubkeyOf(1), unitsToSubunits(DEFAULT_ISSUANCE)),
					TokenIssuance.of(pubkeyOf(2), unitsToSubunits(DEFAULT_ISSUANCE)),
					TokenIssuance.of(pubkeyOf(3), unitsToSubunits(DEFAULT_ISSUANCE)),
					TokenIssuance.of(pubkeyOf(4), unitsToSubunits(DEFAULT_ISSUANCE)),
					TokenIssuance.of(pubkeyOf(5), unitsToSubunits(DEFAULT_ISSUANCE))
				);
			}
			final ImmutableList<TokenIssuance> tokenIssuances = tokenIssuancesBuilder
				.addAll(getTokenIssuances(stakeDelegations))
				.build();

			final long universeTimestamp = TimeUnit.SECONDS.toMillis(universeTimestampSeconds);
			final ECKeyPair universeKey = Keys.readKey(
				universeKeyFile,
				"universe",
				"RADIX_UNIVERSE_KEYSTORE_PASSWORD",
				"RADIX_UNIVERSE_KEY_PASSWORD"
			);

			RadixUniverseBuilder radixUniverseBuilder = Guice.createInjector(new AbstractModule() {
				@Override
				protected void configure() {
					switch (universeType) {
						case PRODUCTION:
							install(new ProductionUniverseConfigModule());
							break;
						case TEST:
							install(new TestUniverseConfigModule());
							break;
						case DEVELOPMENT:
							install(new DevUniverseConfigModule());
							break;
						default:
							throw new IllegalArgumentException("Unknown universe type: " + universeType);
					}
					install(new CryptoModule());
					install(new RadixNativeTokenModule());

					bind(SystemCounters.class).toInstance(new SystemCountersImpl());
					bind(ECKeyPair.class).annotatedWith(Names.named("universeKey")).toInstance(universeKey);
					bind(ClientAtom.class).toProvider(GenesisAtomProvider.class).in(Scopes.SINGLETON);
					bindConstant().annotatedWith(UniverseConfig.class).to(universeTimestamp);
					var selfIssuance = TokenIssuance.of(
						universeKey.getPublicKey(), UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 + 9)
					);
					var allTokenIssuances = Stream.concat(
						tokenIssuances.stream(),
						Stream.of(selfIssuance)
					).collect(ImmutableList.toImmutableList());
					bind(new TypeLiteral<ImmutableList<TokenIssuance>>() { }).annotatedWith(Genesis.class)
						.toInstance(allTokenIssuances);
					bind(new TypeLiteral<ImmutableList<StakeDelegation>>() { }).annotatedWith(Genesis.class)
						.toInstance(stakeDelegations);
					bind(new TypeLiteral<ImmutableList<ECKeyPair>>() { }).annotatedWith(Genesis.class)
						.toInstance(validatorKeys);
				}

				@Provides
				@Named("magic")
				int magic(
					@Named("universeKey") ECKeyPair universeKey,
					@UniverseConfig long universeTimestamp,
					UniverseConfiguration universeConfiguration
				) {
					return Universe.computeMagic(
						universeKey.getPublicKey(),
						universeTimestamp,
						universeConfiguration.getPort(),
						universeConfiguration.getUniverseType()
					);
				}
			}).getInstance(RadixUniverseBuilder.class);

			final Pair<ECKeyPair, Universe> universe = radixUniverseBuilder.build();

			if (outputPrivateKeys) {
				System.out.format("export RADIXDLT_UNIVERSE_PRIVKEY=%s%n", Bytes.toBase64String(universe.getFirst().getPrivateKey()));
				outputNumberedKeys("VALIDATOR_%s", validatorKeys, helmUniverseOutput, awsSecretsUniverseOutput);
				outputNumberedKeys("STAKER_%s", Lists.transform(stakeDelegations, StakeDelegation::staker), helmUniverseOutput, awsSecretsUniverseOutput);
			}
			outputUniverse(suppressCborOutput, suppressJsonOutput, universeType, universe, helmUniverseOutput, awsSecretsUniverseOutput);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			usage(options);
		} catch (IOException | CryptoException e) {
			System.err.println("Error while reading key: " + e.getMessage());
			usage(options);
		}
	}

	private static ImmutableList<ECKeyPair> getValidatorKeys(int validatorsCount) {
		return IntStream.range(0, validatorsCount)
			.mapToObj(n -> {
				try {
					return Keys.readKey(
						String.format(VALIDATOR_TEMPLATE, n),
						"node",
						"RADIX_VALIDATOR_KEYSTORE_PASSWORD",
						"RADIX_VALIDATOR_KEY_PASSWORD"
					);
				} catch (CryptoException | IOException e) {
					throw new IllegalStateException("While reading validator keys", e);
				}
			})
			.collect(ImmutableList.toImmutableList());
	}

	private static ImmutableList<StakeDelegation> getStakeDelegation(List<ECPublicKey> validators, List<UInt256> stakes) {
		int n = 0;
		final ImmutableList.Builder<StakeDelegation> stakeDelegations = ImmutableList.builder();
		final Iterator<UInt256> stakesCycle = Iterators.cycle(stakes);
		for (ECPublicKey validator : validators) {
			try {
				final ECKeyPair stakerKey = Keys.readKey(
					String.format(STAKER_TEMPLATE, n++),
					"wallet",
					"RADIX_STAKER_KEYSTORE_PASSWORD",
					"RADIX_STAKER_KEY_PASSWORD"
				);
				stakeDelegations.add(StakeDelegation.of(stakerKey, validator, stakesCycle.next()));
			} catch (CryptoException | IOException e) {
				throw new IllegalStateException("While reading staker keys", e);
			}
		}
		return stakeDelegations.build();
	}

	// We just generate issuances in the amounts of the stake delegations here
	private static ImmutableList<TokenIssuance> getTokenIssuances(ImmutableList<StakeDelegation> stakeDelegations) {
		return stakeDelegations.stream()
			.map(sd -> TokenIssuance.of(sd.staker().getPublicKey(), sd.amount()))
			.collect(ImmutableList.toImmutableList());
	}

	private static ECPublicKey pubkeyOf(int pk) {
		final byte[] privateKey = new byte[ECKeyPair.BYTES];
		Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);
		ECKeyPair kp;
		try {
			kp = ECKeyPair.fromPrivateKey(privateKey);
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalArgumentException("Error while generating public key", e);
		}
		return kp.getPublicKey();
	}

	private static ImmutableList<UInt256> parseStake(String stakes) {
		return Stream.of(stakes.split(","))
			.map(String::trim)
			.map(BigDecimal::new)
			.map(GenerateUniverses::unitsToSubunits)
			.collect(ImmutableList.toImmutableList());
	}


	private static void outputNumberedKeys(
		String template,
		List<ECKeyPair> keys,
		HelmUniverseOutput helmUniverseOutput,
		AWSSecretsUniverseOutput awsSecretsUniverseOutput
	) {
		int n = 0;
		List<Map<String, Object>> validators = new ArrayList<>();
		final String VALIDATOR_PREFIX = "VALIDATOR";
		boolean compress = false;
		List<String> nodeNames = new ArrayList<>();
		for (ECKeyPair k : keys) {
			Map<String, Object> validator = new HashMap<>();
			String nodeName = String.format("node%s", n);
			String keyname = String.format(template, n++);
			if (!helmUniverseOutput.getOutputHelmValues() && !awsSecretsUniverseOutput.getEnableAwsSecrets()) {
				System.out.format("export RADIXDLT_%s_PRIVKEY=%s%n", keyname, Bytes.toBase64String(k.getPrivateKey()));
			} else if (helmUniverseOutput.getOutputHelmValues() || awsSecretsUniverseOutput.getEnableAwsSecrets()) {
				nodeNames.add(nodeName);
				validator.put("host", nodeName);
				if (template.startsWith(VALIDATOR_PREFIX)) {
					validator.put("seedsRemote", "");
					validator.put("privateKey", Bytes.toBase64String(k.getPrivateKey()));
				} else if (template.startsWith("STAKER")) {
					validator.put("stakingKey", Bytes.toBase64String(k.getPrivateKey()));
				}
				validators.add(validator);
			}
		}

		if (awsSecretsUniverseOutput.getEnableAwsSecrets()) {
			generateAWSSecrets(validators, VALIDATOR_PREFIX, template, awsSecretsUniverseOutput, compress);
		}
		if (helmUniverseOutput.getOutputHelmValues()) {
			generateHelmFiles(validators, VALIDATOR_PREFIX, template, nodeNames, helmUniverseOutput);
		}
	}


	private static void outputUniverse(
		boolean suppressDson,
		boolean suppressJson,
		UniverseType type,
		Pair<ECKeyPair, Universe> p,
		HelmUniverseOutput helmUniverseOutput,
		AWSSecretsUniverseOutput awsSecretsUniverseOutput
	) {
		final Serialization serialization = DefaultSerialization.getInstance();
		final ECKeyPair k = p.getFirst();
		final Universe u = p.getSecond();
		if (!suppressDson) {
			byte[] universeBytes = serialization.toDson(u, Output.WIRE);
			RadixAddress universeAddress = new RadixAddress((byte) u.getMagic(), k.getPublicKey());
			RRI tokenRri = RRI.of(universeAddress, TokenDefinitionUtils.getNativeTokenShortCode());
			if (!helmUniverseOutput.getOutputHelmValues() && !awsSecretsUniverseOutput.getEnableAwsSecrets()) {
				System.out.format("export RADIXDLT_UNIVERSE_TYPE=%s%n", type);
				System.out.format("export RADIXDLT_UNIVERSE_PUBKEY=%s%n", k.getPublicKey().toBase64());
				System.out.format("export RADIXDLT_UNIVERSE_ADDRESS=%s%n", universeAddress);
				System.out.format("export RADIXDLT_UNIVERSE_TOKEN=%s%n", tokenRri);
				System.out.format("export RADIXDLT_UNIVERSE=%s%n", Bytes.toBase64String(universeBytes));
			} else if (helmUniverseOutput.getOutputHelmValues() || awsSecretsUniverseOutput.getEnableAwsSecrets()) {
				Map<String, Map<String, Object>> config = new HashMap<>();
				Map<String, Object> universe = new HashMap<>();
				universe.put("type", type);
				universe.put("privKey", Bytes.toBase64String(p.getFirst().getPrivateKey()));
				universe.put("pubkey", k.getPublicKey().toBase64());
				universe.put("address", universeAddress.toString());
				universe.put("token", tokenRri.toString());

				JSONObject universeJson = new JSONObject(serialization.toJson(p.getSecond(), Output.WIRE));
				universe.put("value", universeJson.toString());

				config.put("universe", universe);

				if (helmUniverseOutput.getOutputHelmValues()) {
					String filename = String.format("%s/universe.yaml", helmUniverseOutput.getHelmValuesPath());
					writeYamlOutput(filename, config);
				} else if (awsSecretsUniverseOutput.getEnableAwsSecrets()) {
					boolean compress = true;
					String secretName = String.format("%s/universe", awsSecretsUniverseOutput.getNetworkName());
					writeAWSSecret(universe, secretName, awsSecretsUniverseOutput, compress);
				}
			}
		}
		if (!suppressJson) {
			JSONObject json = new JSONObject(serialization.toJson(p.getSecond(), Output.WIRE));
			System.out.println(json.toString(4));
		}
	}

	private static void usage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(GenerateUniverses.class.getSimpleName(), options, true);
	}

	private static UniverseType parseUniverseType(String type) {
		return UniverseType.valueOf(type.toUpperCase());
	}

	private static Optional<String> getOption(CommandLine cmd, char opt) {
		String value = cmd.getOptionValue(opt);
		return Optional.ofNullable(value);
	}

	private static UInt256 unitsToSubunits(BigDecimal units) {
		return UInt256s.fromBigDecimal(units.multiply(SUB_UNITS_BIG_DECIMAL));
	}

	private static void writeYamlOutput(String fileName, Map<String, Map<String, Object>> validatorsOut) {
		ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
		try {
			String yaml = objectMapper.writeValueAsString(validatorsOut);
			try (FileWriter file = new FileWriter(fileName)) {
				file.write(yaml);
				file.flush();
			}

		} catch (JsonProcessingException e) {
			throw new IllegalStateException("While creating YAML", e);
		} catch (IOException e) {
			throw new IllegalStateException("While writing Helm values files", e);
		}
	}

	public static void writeAWSSecret(Map<String, Object> awsSecret, String secretName, AWSSecretsUniverseOutput awsSecretsUniverseOutput, boolean compress) {
		if (AWSSecretManager.awsSecretExists(secretName)) {
			AWSSecretManager.updateAWSSecret(awsSecret, secretName, awsSecretsUniverseOutput, compress);
		} else {
			AWSSecretManager.createAWSSecret(awsSecret, secretName, awsSecretsUniverseOutput, compress);
		}
	}

	private static void generateHelmFiles(
		final List<Map<String, Object>> validators,
		final String validatorPrefix,
		final String template,
		final List<String> nodeNames,
		final HelmUniverseOutput helmUniverseOutput
		) {
		for (Map<String, Object> validator : validators) {
			String name = (String) validator.get("host");
			Map<String, Map<String, Object>> validatorsOut = new HashMap<>();
			String fileName = String.format("%s/%s-staker.yaml", helmUniverseOutput.getHelmValuesPath(), name);
			if (template.startsWith(validatorPrefix)) {
				List<String> seedsRemote = new ArrayList<>(nodeNames);
				seedsRemote.remove(name);
				validator.put("seedsRemote", Strings.join(seedsRemote, ','));
				validator.put("allNodes", Strings.join(nodeNames, ','));
				validatorsOut.put("validator", validator);
				fileName = String.format("%s/%s-validator.yaml", helmUniverseOutput.getHelmValuesPath(), name);
			} else if (template.startsWith("STAKER")) {
				validatorsOut.put("staker", validator);
			}

			writeYamlOutput(fileName, validatorsOut);
			System.out.format("Helm value files created in %s%n", helmUniverseOutput.getHelmValuesPath());
		}
	}


	private static void generateAWSSecrets(
		final List<Map<String, Object>> validators,
		final String validatorPrefix,
		final String template,
		final AWSSecretsUniverseOutput awsSecretsUniverseOutput,
		boolean compress
	) {
		for (Map<String, Object> validator : validators) {
			Map<String, Object> awsSecret = new HashMap<>();
			String name = (String) validator.get("host");
			if (template.startsWith(validatorPrefix)) {
				awsSecret.put("key", validator.get("privateKey"));
			} else if (template.startsWith("STAKER")) {
				awsSecret.put("key", validator.get("stakingKey"));
			}
			String secretName = String.format("%s/%s/staker", awsSecretsUniverseOutput.getNetworkName(), name);
			if (template.startsWith(validatorPrefix)) {
				secretName = String.format("%s/%s/validator", awsSecretsUniverseOutput.getNetworkName(), name);
			}
			writeAWSSecret(awsSecret, secretName, awsSecretsUniverseOutput, compress);
			System.out.format(
				"AWS secrets created for network %s %s%n",
				awsSecretsUniverseOutput.getEnableAwsSecrets(),
				awsSecretsUniverseOutput.getNetworkName()
			);
		}
	}
}
