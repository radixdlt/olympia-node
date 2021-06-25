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

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.atom.Txn;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.universe.Network;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.util.Strings;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import org.radix.universe.output.HelmUniverseOutput;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import com.radixdlt.CryptoModule;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.CryptoException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.NodeAddress;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.keys.Keys;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.GenesisProvider;
import com.radixdlt.utils.AWSSecretManager;
import com.radixdlt.utils.AWSSecretsOutputOptions;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt256s;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Security;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class GenerateUniverses {
	private GenerateUniverses() { }

	private static final BigDecimal SUB_UNITS_BIG_DECIMAL
		= new BigDecimal(UInt256s.toBigInteger(TokenDefinitionUtils.SUB_UNITS));
	private static final String DEFAULT_TIMESTAMP = String.valueOf(Instant.parse("2020-01-01T00:00:00.00Z").getEpochSecond());
	private static final String VALIDATOR_TEMPLATE = "validator%s.ks";
	private static final String STAKER_TEMPLATE = "staker%s.ks";
	private static final String DEFAULT_HELM_VALUES_OUTPUT_DIRECTORY = ".";
	private static final Boolean DEFAULT_HELM_OUTPUT = false;
	private static final Boolean DEFAULT_ENABLE_AWS_SECRETS = false;
	private static final Boolean DEFAULT_RECREATE_AWS_SECRETS = false;

	private static final UInt256 DEFAULT_ISSUANCE =
		UInt256.from("1000000000000000000000000000").multiply(TokenDefinitionUtils.SUB_UNITS);
	private static final BigDecimal DEFAULT_STAKE = BigDecimal.valueOf(1_000_000L);

	public static void main(String[] args) {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);

		Options options = new Options();
		options.addOption("h", "help",                   false, "Show usage information (this message)");
		options.addOption("c", "no-cbor-output",         false, "Suppress DSON output");
		options.addOption("i", "issue-default-tokens",   false, "Issue tokens to default keys 1, 2, 3, 4 and 5 (dev universe only)");
		options.addOption("pk", "pubkey-issuance",   true, "Pub key (hex) to issue tokens to");
		options.addOption("m", "amount-issuance",   true, "Amount to issue pub key");
		options.addOption("j", "no-json-output",         false, "Suppress JSON output");
		options.addOption("p", "include-private-keys",   false, "Include validator and staking private keys in output");
		options.addOption("n", "network-id",          true,  "Specify network id (default: " + Network.LOCALNET.getId() + ")");
		options.addOption("T", "universe-timestamp",     true,  "Specify universe timestamp (default: " + DEFAULT_TIMESTAMP + ")");
		options.addOption("v", "validator-count",        true,  "Specify number of validators to generate (required)");
		options.addOption("hc", "helm-configuration", true, "Generates Helm values for validators(default: " + DEFAULT_HELM_OUTPUT + ")");
		options.addOption("d", "helm-output-directory", true, "Output dir to add Helm values files(default: " + DEFAULT_HELM_VALUES_OUTPUT_DIRECTORY + ")");
		options.addOption("as", "enable-aws-secrets", true, "Store as AWS Secrets(default: " + DEFAULT_ENABLE_AWS_SECRETS + ")");
		options.addOption("rs", "recreate-aws-secrets", true, "Recreate AWS Secrets(default: " + DEFAULT_RECREATE_AWS_SECRETS + ")");

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cmd = parser.parse(options, args);
			if (!cmd.getArgList().isEmpty()) {
				System.err.println("Extra arguments: " + String.join(" ", cmd.getArgList()));
				usage(options);
				return;
			}

			if (cmd.hasOption('h')) {
				usage(options);
				return;
			}

			final boolean suppressCborOutput = cmd.hasOption('c');
			final boolean suppressJsonOutput = cmd.hasOption('j');
			final boolean outputPrivateKeys = cmd.hasOption('p');

			final boolean outputHelmValues = Boolean.parseBoolean(cmd.getOptionValue("hc"));
			final String helmValuesPath = getOption(cmd, 'd').orElse(DEFAULT_HELM_VALUES_OUTPUT_DIRECTORY);
			final HelmUniverseOutput helmUniverseOutput = new HelmUniverseOutput(outputHelmValues, helmValuesPath);
			final boolean enableAwsSecrets = Boolean.parseBoolean(cmd.getOptionValue("as"));
			final boolean recreateAwsSecrets = Boolean.parseBoolean(cmd.getOptionValue("rs"));
			final int networkId = getOption(cmd, 'n').map(Integer::parseInt).orElse(Network.LOCALNET.getId());
			final AWSSecretsOutputOptions awsSecretsOutputOptions = new AWSSecretsOutputOptions(
				enableAwsSecrets, recreateAwsSecrets, "Network" + networkId
			);

			final ImmutableList<UInt256> stakes = ImmutableList.of(unitsToSubunits(DEFAULT_STAKE));
			final long timestampSeconds = Long.parseLong(getOption(cmd, 'T').orElse(DEFAULT_TIMESTAMP));
			final int validatorsCount = cmd.getOptionValue("v") != null ? Integer.parseInt(cmd.getOptionValue("v")) : 0;
			final String listOfValidatorsEnv = Optional
				.ofNullable((System.getenv("NODE_NAMES")))
				.orElse("");
			final List<String> listOfValidators;

			if (!listOfValidatorsEnv.trim().equals("")) {
				listOfValidators = List.of(listOfValidatorsEnv.split(",")).stream()
					.map(entry -> entry.replaceAll("[^\\w-]", ""))
					.collect(Collectors.toList());
			} else {
				listOfValidators = new ArrayList<>();
			}

			if (stakes.isEmpty()) {
				throw new IllegalArgumentException("Must specify at least one staking amount");
			}

			final ImmutableList<KeyDetails> keyDetails;
			final ImmutableList<KeyDetails> keysDetailsWithStakeDelegation;
			if (validatorsCount > 0) {
				keyDetails = getValidatorKeys(validatorsCount);
				keysDetailsWithStakeDelegation = getStakeDelegation(keyDetails, stakes);
			} else {
				keyDetails = getValidatorKeys(listOfValidators);
				keysDetailsWithStakeDelegation = getStakeDelegationUsingExistingKeyList(keyDetails, stakes);
			}

			final ImmutableList<ECKeyPair> validatorKeys = keyDetails.stream()
				.map(KeyDetails::getKeyPair)
				.collect(ImmutableList.toImmutableList());

			final ImmutableList.Builder<TokenIssuance> tokenIssuancesBuilder = ImmutableList.builder();
			if (networkId != Network.MAINNET.getId() && cmd.hasOption("i")) {
				tokenIssuancesBuilder.add(
					TokenIssuance.of(pubkeyOf(1), DEFAULT_ISSUANCE),
					TokenIssuance.of(pubkeyOf(2), DEFAULT_ISSUANCE),
					TokenIssuance.of(pubkeyOf(3), DEFAULT_ISSUANCE),
					TokenIssuance.of(pubkeyOf(4), DEFAULT_ISSUANCE),
					TokenIssuance.of(pubkeyOf(5), DEFAULT_ISSUANCE)
				);
			}

			var additionalActions = new ArrayList<TxAction>();

			final String hexPubKey;
			if (cmd.hasOption("pk")) {
				hexPubKey = cmd.getOptionValue("pk");
			} else {
				hexPubKey = "03fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556";
			}

			final ECPublicKey pubKey;
			try {
				pubKey = ECPublicKey.fromHex(hexPubKey);
			} catch (PublicKeyException e) {
				throw new IllegalStateException("Invalid pub key", e);
			}
			final UInt256 tokenAmt;
			if (cmd.hasOption("a")) {
				var amountStr = cmd.getOptionValue("a");
				var amt = new BigInteger(amountStr);
				tokenAmt = unitsToSubunits(new BigDecimal(amt));
			} else {
				tokenAmt = DEFAULT_ISSUANCE;
			}
			tokenIssuancesBuilder.add(TokenIssuance.of(pubKey, tokenAmt));
			var tokensToCreate = Map.of(
				"gum", "Gumballs",
				"emunie", "eMunie Tokens",
				"cerb", "Cerbys Special Tokens"
			);

			var accountAddr = REAddr.ofPubKeyAccount(pubKey);
			var resourceAddrs = new ArrayList<REAddr>();
			tokensToCreate.forEach((symbol, name) -> {
				var resourceAddr = REAddr.ofHashedKey(pubKey, symbol);
				resourceAddrs.add(resourceAddr);
				additionalActions.add(new CreateFixedToken(
					resourceAddr,
					accountAddr,
					symbol, name, "", "", "",
					tokenAmt
				));
			});

			if (networkId != Network.MAINNET.getId()) {
				// Issue tokens to initial validators for now to support application services
				// FIXME: Remove this
				validatorKeys
					.forEach(kp -> {
						var tokenIssuance = TokenIssuance.of(kp.getPublicKey(), DEFAULT_ISSUANCE);
						tokenIssuancesBuilder.add(tokenIssuance);
						var keyAddr = REAddr.ofPubKeyAccount(kp.getPublicKey());
						resourceAddrs.forEach(addr -> {
							additionalActions.add(
								new TransferToken(addr, accountAddr, keyAddr, unitsToSubunits(BigDecimal.valueOf(1_000_000L)))
							);
						});
					});
			}

			var stakeDelegations = keyDetails.stream().map(KeyDetails::getKeyPair)
				.map(k -> StakeDelegation.of(pubKey, k.getPublicKey(), unitsToSubunits(DEFAULT_STAKE)))
				.collect(ImmutableList.toImmutableList());

			final long timestamp = TimeUnit.SECONDS.toMillis(timestampSeconds);

			var genesisProvider = Guice.createInjector(new AbstractModule() {
				@Provides
				@Singleton
				RERules reRules(Forks forks) {
					return forks.get(0);
				}

				@Override
				protected void configure() {
					install(new CryptoModule());
					install(new ForksModule());
					install(RadixEngineConfig.asModule(1, 100, 50));
					bind(new TypeLiteral<List<TxAction>>() {}).annotatedWith(Genesis.class).toInstance(additionalActions);
					bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
					bind(SystemCounters.class).toInstance(new SystemCountersImpl());
					bindConstant().annotatedWith(Genesis.class).to(timestamp);
					bind(new TypeLiteral<ImmutableList<TokenIssuance>>() {}).annotatedWith(Genesis.class)
						.toInstance(tokenIssuancesBuilder.build());
					bind(new TypeLiteral<ImmutableList<StakeDelegation>>() {}).annotatedWith(Genesis.class)
						.toInstance(stakeDelegations);
					bind(new TypeLiteral<ImmutableList<ECKeyPair>>() {}).annotatedWith(Genesis.class)
						.toInstance(validatorKeys);
				}
			}).getInstance(GenesisProvider.class);

			var genesis = genesisProvider.get().getTxns().get(0);

			if (outputPrivateKeys) {
				outputNumberedKeys("VALIDATOR_%s", keysDetailsWithStakeDelegation, helmUniverseOutput, awsSecretsOutputOptions);
				outputNumberedKeys("STAKER_%s", keysDetailsWithStakeDelegation, helmUniverseOutput, awsSecretsOutputOptions);
			}
			outputUniverse(suppressCborOutput, suppressJsonOutput, networkId, genesis, helmUniverseOutput, awsSecretsOutputOptions);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			usage(options);
		}
	}

	private static ImmutableList<KeyDetails> getValidatorKeys(int validatorsCount) {
		var nodeNamePrefix = Optional
			.ofNullable(System.getenv("NODE_NAME_PREFIX"))
			.orElse("node");

		return IntStream.range(0, validatorsCount)
			.mapToObj(n -> {
				var nodeName = String.format("%s%s", nodeNamePrefix, n);
				var keypath = String.format(VALIDATOR_TEMPLATE, n);
				var key = getValidatorEcKeyPair(keypath);
				return new KeyDetails(nodeName, key, keypath);
			})
			.collect(ImmutableList.toImmutableList());
	}

	private static ImmutableList<KeyDetails> getValidatorKeys(List<String> validators) {
		return validators.stream()
			.map(validator -> {
				var keyPath = String.format("%s.ks", validator);
				var key = getValidatorEcKeyPair(keyPath);
				return new KeyDetails(validator, key, keyPath);
			})
			.collect(ImmutableList.toImmutableList());
	}

	private static ECKeyPair getValidatorEcKeyPair(String keyPath) {
		try {
			return Keys.readValidatorKey(keyPath);
		} catch (CryptoException | IOException e) {
			throw new IllegalStateException("While reading validator keys", e);
		}
	}

	private static ImmutableList<KeyDetails> getStakeDelegation(List<KeyDetails> keyDetails, List<UInt256> stakes) {
		var stakesCycle = Iterators.cycle(stakes);
		var nodeNamePrefix = Optional.ofNullable(System.getenv("NODE_NAME_PREFIX")).orElse("node");

		return IntStream.range(0, keyDetails.size()).mapToObj(
			n -> {
				var nodeName = String.format("%s%s", nodeNamePrefix, n);
				var stakerKeypath = String.format(STAKER_TEMPLATE, n);
				var validator = keyDetails.get(n);
				validator.setNodeName(nodeName);
				return getStakeDelegation(stakesCycle, stakerKeypath, validator);
			}
		).collect(ImmutableList.toImmutableList());
	}

	private static ImmutableList<KeyDetails> getStakeDelegationUsingExistingKeyList(
		List<KeyDetails> keyDetails,
		List<UInt256> stakes
	) {
		final Iterator<UInt256> stakesCycle = Iterators.cycle(stakes);
		return keyDetails.stream().map(
			keyDetail -> {
				var stakerKeyStore = String.format("%s_staker.ks", keyDetail.getNodeName());
				return getStakeDelegation(stakesCycle, stakerKeyStore, keyDetail);
			}
		).collect(ImmutableList.toImmutableList());
	}

	private static KeyDetails getStakeDelegation(Iterator<UInt256> stakesCycle, String keyStore, KeyDetails validator) {
		try {
			var stakerKey = Keys.readStakerKey(keyStore);
			var stakeDelegation = StakeDelegation.of(
				stakerKey.getPublicKey(),
				validator.getKeyPair().getPublicKey(),
				stakesCycle.next()
			);
			validator.setStakeDelegation(stakeDelegation);
			validator.setStakerKeyStore(keyStore);
			return validator;
		} catch (CryptoException | IOException e) {
			throw new IllegalStateException("While reading staker keys", e);
		}
	}

	private static ECPublicKey pubkeyOf(int pk) {
		var privateKey = new byte[ECKeyPair.BYTES];
		Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);

		try {
			return ECKeyPair.fromPrivateKey(privateKey).getPublicKey();
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalArgumentException("Error while generating public key", e);
		}
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
		List<KeyDetails> keys,
		HelmUniverseOutput helmUniverseOutput,
		AWSSecretsOutputOptions awsSecretsOutputOptions
	) {
		final String VALIDATOR_PREFIX = "VALIDATOR";

		if (isStdOutput(helmUniverseOutput, awsSecretsOutputOptions)) {
			IntStream.range(0, keys.size()).forEach(i -> {
				String keyname = String.format(template, i);
				System.out.format("export RADIXDLT_%s_PRIVKEY=%s%n", keyname, Bytes.toBase64String(keys.get(i).getKeyPair().getPrivateKey()));
				System.out.format("export RADIXDLT_%s_PUBKEY=%s%n", keyname, NodeAddress.of(keys.get(i).getKeyPair().getPublicKey()));
			});
		}

		if (isHelmOrAwsOutuput(helmUniverseOutput, awsSecretsOutputOptions)) {
			var validators = IntStream.range(0, keys.size())
				.mapToObj(i -> {
					Map<String, Object> validator = new HashMap<>();

					validator.put("node", i);
					validator.put("host", keys.get(i).getNodeName());
					validator.put("validatorKeyStore", keys.get(i).getValidatorKeyStore());
					validator.put("stakerKeyStore", keys.get(i).getStakerKeyStore());
					if (template.startsWith(VALIDATOR_PREFIX)) {
						validator.put("seedsRemote", "");
						validator.put("privateKey", Bytes.toBase64String(keys.get(i).getKeyPair().getPrivateKey()));
					}
					return validator;
				})
				.collect(Collectors.toList());

			var nodeNames = validators.stream()
				.map(v -> v.get("host"))
				.map(String.class::cast)
				.collect(Collectors.toList());

			if (awsSecretsOutputOptions.getEnableAwsSecrets()) {
				generateValidatorsAWSSecrets(validators, VALIDATOR_PREFIX, template, awsSecretsOutputOptions, false);
			}
			if (helmUniverseOutput.getOutputHelmValues()) {
				generateHelmFiles(validators, VALIDATOR_PREFIX, template, nodeNames, helmUniverseOutput);
			}
		}
	}

	private static boolean isStdOutput(HelmUniverseOutput helmUniverseOutput, AWSSecretsOutputOptions awsSecretsOutputOptions) {
		return !helmUniverseOutput.getOutputHelmValues() && !awsSecretsOutputOptions.getEnableAwsSecrets();
	}

	private static void outputUniverse(
		boolean suppressDson,
		boolean suppressJson,
		int networkId,
		Txn genesis,
		HelmUniverseOutput helmUniverseOutput,
		AWSSecretsOutputOptions awsSecretsOutputOptions
	) {
		if (!suppressDson) {
			if (isStdOutput(helmUniverseOutput, awsSecretsOutputOptions)) {
				System.out.format("export RADIXDLT_GENESIS_TXN=%s%n", Bytes.toHexString(genesis.getPayload()));
			} else if (isHelmOrAwsOutuput(helmUniverseOutput, awsSecretsOutputOptions)) {
				Map<String, Map<String, Object>> config = new HashMap<>();
				Map<String, Object> universe = new HashMap<>();
				universe.put("networkId", networkId);
				universe.put("value", new JSONObject().put("genesis", Bytes.toHexString(genesis.getPayload())));
				config.put("universe", universe);

				if (helmUniverseOutput.getOutputHelmValues()) {
					String filename = String.format("%s/universe.yaml", helmUniverseOutput.getHelmValuesPath());
					writeYamlOutput(filename, config);
				} else if (awsSecretsOutputOptions.getEnableAwsSecrets()) {
					boolean compress = true;
					String secretName = String.format("%s/universe", awsSecretsOutputOptions.getNetworkName());
					writeTextAWSSecret(universe, secretName, awsSecretsOutputOptions, compress);
				}
			}
		}
		if (!suppressJson) {
			System.out.println(new JSONObject().put("genesis", Bytes.toHexString(genesis.getPayload())));
		}
	}

	private static boolean isHelmOrAwsOutuput(HelmUniverseOutput helmUniverseOutput, AWSSecretsOutputOptions awsSecretsOutputOptions) {
		return helmUniverseOutput.getOutputHelmValues() || awsSecretsOutputOptions.getEnableAwsSecrets();
	}

	private static void usage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(GenerateUniverses.class.getSimpleName(), options, true);
	}

	private static Optional<String> getOption(CommandLine cmd, char opt) {
		String value = cmd.getOptionValue(opt);
		return Optional.ofNullable(value);
	}

	private static UInt256 unitsToSubunits(BigDecimal units) {
		return UInt256s.fromBigDecimal(units.multiply(SUB_UNITS_BIG_DECIMAL));
	}

	private static void writeYamlOutput(String fileName, Map<String, Map<String, Object>> validatorsOut) {
		var objectMapper = new ObjectMapper(new YAMLFactory());

		try {
			var yaml = objectMapper.writeValueAsString(validatorsOut);

			try (var file = new FileWriter(fileName)) {
				file.write(yaml);
				file.flush();
			}
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("While creating YAML", e);
		} catch (IOException e) {
			throw new IllegalStateException("While writing Helm values files", e);
		}
	}

	public static void writeBinaryAWSSecret(Map<String, Object> awsSecret, String secretName, AWSSecretsOutputOptions awsSecretsOutputOptions, boolean compress) {
		writeAWSSecret(awsSecret, secretName, awsSecretsOutputOptions, compress, true);
	}

	public static void writeTextAWSSecret(Map<String, Object> awsSecret, String secretName, AWSSecretsOutputOptions awsSecretsOutputOptions, boolean compress) {
		writeAWSSecret(awsSecret, secretName, awsSecretsOutputOptions, compress, false);
	}

	public static void writeAWSSecret(Map<String, Object> awsSecret, String secretName, AWSSecretsOutputOptions awsSecretsOutputOptions, boolean compress, boolean binarySecret) {
		if (AWSSecretManager.awsSecretExists(secretName)) {
			AWSSecretManager.updateAWSSecret(awsSecret, secretName, awsSecretsOutputOptions, compress, binarySecret);
		} else {
			AWSSecretManager.createAWSSecret(awsSecret, secretName, awsSecretsOutputOptions, compress, binarySecret);
		}
	}

	private static void generateHelmFiles(
		final List<Map<String, Object>> validators,
		final String validatorPrefix,
		final String template,
		final List<String> nodeNames,
		final HelmUniverseOutput helmUniverseOutput
	) {
		for (var validator : validators) {
			var name = (String) validator.get("host");
			var validatorsOut = new HashMap<String, Map<String, Object>>();
			var fileName = String.format("%s/%s-staker.yaml", helmUniverseOutput.getHelmValuesPath(), name);

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


	private static void generateValidatorsAWSSecrets(
		final List<Map<String, Object>> validators,
		final String validatorPrefix,
		final String template,
		final AWSSecretsOutputOptions awsSecretsOutputOptions,
		boolean compress
	) {
		validators.forEach(v -> {
			var validatorAwsSecret = new HashMap<String, Object>();
			var name = (String) v.get("host");
			Object keyData;
			String keyFile, secretName, keyFileSecretName;

			if (template.startsWith("STAKER")) {
				keyData = v.get("stakingKey");
				keyFile = (String) v.get("stakerKeyStore"); // keyfile name is from the
				secretName = String.format("%s/%s/staker", awsSecretsOutputOptions.getNetworkName(), name);
				keyFileSecretName = String.format("%s/%s/staker_key", awsSecretsOutputOptions.getNetworkName(), name);
			} else if (template.startsWith(validatorPrefix)) {
				keyData = v.get("privateKey");
				keyFile = (String) v.get("validatorKeyStore"); // keyfile name is from the
				secretName = String.format("%s/%s/validator", awsSecretsOutputOptions.getNetworkName(), name);
				keyFileSecretName = String.format("%s/%s/validator_key", awsSecretsOutputOptions.getNetworkName(), name);
			} else {
				throw new IllegalStateException("Invalid template string");
			}
			validatorAwsSecret.put("key", keyData);
			writeTextAWSSecret(validatorAwsSecret, secretName, awsSecretsOutputOptions, compress);

			var keyFileAwsSecret = new HashMap<String, Object>();
			try {
				var data = Files.readAllBytes(Paths.get(keyFile));
				keyFileAwsSecret.put("key", data);
			} catch (IOException e) {
				throw new IllegalStateException("While reading keys", e);
			}
			writeBinaryAWSSecret(keyFileAwsSecret, keyFileSecretName, awsSecretsOutputOptions, compress);
		});

		System.out.format(
			"AWS secrets created for network %s %s%n",
			awsSecretsOutputOptions.getEnableAwsSecrets(),
			awsSecretsOutputOptions.getNetworkName()
		);
	}
}

class KeyDetails {
	private String nodeName;
	private String validatorKeyStore;
	private String stakerKeyStore;
	private ECKeyPair keyPair;
	private StakeDelegation stakeDelegation;

	KeyDetails(String nodeName, ECKeyPair keyPair, String validatorKeyStore) {
		this.nodeName = nodeName;
		this.keyPair = keyPair;
		this.validatorKeyStore = validatorKeyStore;
	}

	public void setStakeDelegation(StakeDelegation stakeDelegation) {
		this.stakeDelegation = stakeDelegation;
	}

	public String getNodeName() {
		return nodeName;
	}

	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

	public ECKeyPair getKeyPair() {
		return keyPair;
	}

	public StakeDelegation getStakeDelegation() {
		return stakeDelegation;
	}

	public String getValidatorKeyStore() {
		return validatorKeyStore;
	}

	public String getStakerKeyStore() {
		return stakerKeyStore;
	}

	public void setStakerKeyStore(String stakerKeyStore) {
		this.stakerKeyStore = stakerKeyStore;
	}
}
