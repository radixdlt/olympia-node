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
import com.radixdlt.CryptoModule;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.radixdlt.crypto.exception.CryptoException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;

import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.checkpoint.RadixNativeTokenModule;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.GenesisProvider;
import org.apache.logging.log4j.util.Strings;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.Rri;
import com.radixdlt.keys.Keys;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt256s;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import com.radixdlt.utils.AWSSecretManager;
import com.radixdlt.utils.AWSSecretsOutputOptions;
import org.radix.universe.output.HelmUniverseOutput;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
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
		options.addOption("p", "pubkey-issuance",   true, "Pub key (hex) to issue tokens to");
		options.addOption("m", "amount-issuance",   true, "Amount to issue pub key");
		options.addOption("j", "no-json-output",         false, "Suppress JSON output");
		options.addOption("p", "include-private-keys",   false, "Include validator and staking private keys in output");
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
			final boolean outputPrivateKeys = cmd.hasOption('p');

			final boolean outputHelmValues = Boolean.parseBoolean(cmd.getOptionValue("hc"));
			final String helmValuesPath = getOption(cmd, 'd').orElse(DEFAULT_HELM_VALUES_OUTPUT_DIRECTORY);
			final HelmUniverseOutput helmUniverseOutput = new HelmUniverseOutput(outputHelmValues, helmValuesPath);
			final boolean enableAwsSecrets = Boolean.parseBoolean(cmd.getOptionValue("as"));
			final boolean recreateAwsSecrets = Boolean.parseBoolean(cmd.getOptionValue("rs"));
			final String networkName = getOption(cmd, 'n').orElse(DEFAULT_NETWORK_NAME);
			final AWSSecretsOutputOptions awsSecretsOutputOptions = new AWSSecretsOutputOptions(enableAwsSecrets, recreateAwsSecrets, networkName);

			final ImmutableList<UInt256> stakes = parseStake(getOption(cmd, 'S').orElse(DEFAULT_STAKE));
			final UniverseType universeType = parseUniverseType(getOption(cmd, 't').orElse(DEFAULT_UNIVERSE));
			final long timestampSeconds = Long.parseLong(getOption(cmd, 'T').orElse(DEFAULT_TIMESTAMP));
			final int validatorsCount = cmd.getOptionValue("v") !=null ?  Integer.parseInt(cmd.getOptionValue("v")) : 0;
			final String listOfValidatorsEnv= Optional
				.ofNullable(
					(System.getenv("NODE_NAMES")))
				.orElse( "");
			final List<String> listOfValidators;

			if(!listOfValidatorsEnv.trim().equals("")){
				List<String> rawlist = new ArrayList<String>(Arrays.asList(listOfValidatorsEnv.split(",")));
				listOfValidators = rawlist.stream()
					.map(entry -> entry.replaceAll("[^\\w-]",""))
					.collect(Collectors.toList());
			}
			else{
				listOfValidators = new ArrayList<>();
			}

			if (stakes.isEmpty()) {
				throw new IllegalArgumentException("Must specify at least one staking amount");
			}
			/*
			if (validatorsCount <= 0  && listOfValidators.size() <= 0) {
				throw new IllegalArgumentException("There must be at least one validator");
			}

			 */

			final ImmutableList<KeyDetails> keyDetails;
			final ImmutableList<KeyDetails> keysDetailsWithStakeDelegation;
			if(validatorsCount > 0){
				keyDetails = getValidatorKeys(validatorsCount);
				keysDetailsWithStakeDelegation = getStakeDelegation(keyDetails, stakes);
			}else {
				keyDetails = getValidatorKeys(listOfValidators);
				keysDetailsWithStakeDelegation = getStakeDelegationUsingExistingKeyList(keyDetails, stakes);
			}

			final ImmutableList<ECKeyPair> validatorKeys = keyDetails.stream()
				.map(KeyDetails::getKeyPair)
				.collect(ImmutableList.toImmutableList());
			final ImmutableList<StakeDelegation> stakeDelegations = keysDetailsWithStakeDelegation.stream()
				.map(KeyDetails::getStakeDelegation)
				.collect(ImmutableList.toImmutableList());

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

			if (cmd.hasOption("p")) {
				try {
					var hexPubKey = cmd.getOptionValue("p");
					var pubKey = ECPublicKey.fromHex(hexPubKey);
					final UInt256 tokenAmt;
					if (cmd.hasOption("a")) {
						var amountStr = cmd.getOptionValue("a");
						var amt = new BigInteger(amountStr);
						tokenAmt = unitsToSubunits(new BigDecimal(amt));
					} else {
						tokenAmt = unitsToSubunits(DEFAULT_ISSUANCE);
					}
					tokenIssuancesBuilder.add(TokenIssuance.of(pubKey, tokenAmt));
				} catch (PublicKeyException e) {
					throw new IllegalStateException("Invalid pub key", e);
				}
			}

			if (universeType == UniverseType.DEVELOPMENT) {
				// Issue tokens to initial validators for now to support application services
				// FIXME: Remove this
				validatorKeys.stream()
					.map(kp -> TokenIssuance.of(kp.getPublicKey(), unitsToSubunits(DEFAULT_ISSUANCE)))
					.forEach(tokenIssuancesBuilder::add);
			}

			final ImmutableList<TokenIssuance> tokenIssuances = tokenIssuancesBuilder
				.addAll(getTokenIssuances(stakeDelegations))
				.build();

			final long timestamp = TimeUnit.SECONDS.toMillis(timestampSeconds);

			RadixUniverseBuilder radixUniverseBuilder = Guice.createInjector(new AbstractModule() {
				@Override
				protected void configure() {
					bind(UniverseType.class).toInstance(universeType);
					install(new CryptoModule());
					install(new RadixNativeTokenModule());
					install(RadixEngineConfig.createModule(1, 100, 10000L));
					install(new RadixEngineModule());

					bind(new TypeLiteral<EngineStore<LedgerAndBFTProof>>() { }).toInstance(new InMemoryEngineStore<>());
					bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
					bind(SystemCounters.class).toInstance(new SystemCountersImpl());
					bind(new TypeLiteral<VerifiedTxnsAndProof>() { }).toProvider(GenesisProvider.class).in(Scopes.SINGLETON);
					bindConstant().annotatedWith(Genesis.class).to(timestamp);
					bind(new TypeLiteral<ImmutableList<TokenIssuance>>() { }).annotatedWith(Genesis.class)
						.toInstance(tokenIssuances);
					bind(new TypeLiteral<ImmutableList<StakeDelegation>>() { }).annotatedWith(Genesis.class)
						.toInstance(stakeDelegations);
					bind(new TypeLiteral<ImmutableList<ECKeyPair>>() { }).annotatedWith(Genesis.class)
						.toInstance(validatorKeys);
				}

				@Provides
				@Named("magic")
				int magic(UniverseType universeType) {
					return Universe.computeMagic(universeType);
				}
			}).getInstance(RadixUniverseBuilder.class);

			final var universe = radixUniverseBuilder.build();

			if (outputPrivateKeys) {
				outputNumberedKeys("VALIDATOR_%s", keysDetailsWithStakeDelegation, helmUniverseOutput, awsSecretsOutputOptions);
				outputNumberedKeys("STAKER_%s", keysDetailsWithStakeDelegation, helmUniverseOutput,
					awsSecretsOutputOptions);
			}
			outputUniverse(suppressCborOutput, suppressJsonOutput, universeType, universe, helmUniverseOutput, awsSecretsOutputOptions);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			usage(options);
		}
	}

	private static ImmutableList<KeyDetails> getValidatorKeys(int validatorsCount) {
		String nodeNamePrefix =  Optional
			.ofNullable(System.getenv("NODE_NAME_PREFIX"))
			.orElse("node");

		return IntStream.range(0, validatorsCount)
			.mapToObj(n -> {
				String nodeName = String.format("%s%s",nodeNamePrefix, n);
				String keypath = String.format(VALIDATOR_TEMPLATE, n);
				ECKeyPair key;
				key = getValidatorEcKeyPair(keypath);
				return new KeyDetails(nodeName,key,keypath);
			})
			.collect(ImmutableList.toImmutableList());
	}

	private static ImmutableList<KeyDetails> getValidatorKeys(List<String> validators) {
		return validators.stream()
			.map(validator -> {
				String keypath = String.format("%s.ks",validator);
				ECKeyPair key = getValidatorEcKeyPair(keypath);
				return new KeyDetails(validator,key,keypath);
			})
			.collect(ImmutableList.toImmutableList());
	}

	private static ECKeyPair getValidatorEcKeyPair(String keypath) {
		ECKeyPair key;
		try {
			key = Keys.readKey(
				keypath,
				"node",
				"RADIX_VALIDATOR_KEYSTORE_PASSWORD",
				"RADIX_VALIDATOR_KEY_PASSWORD");
		} catch (CryptoException | IOException e) {
			throw new IllegalStateException("While reading validator keys", e);
		}
		return key;
	}

	private static ImmutableList<KeyDetails> getStakeDelegation(List<KeyDetails> keyDetails, List<UInt256> stakes) {
		final Iterator<UInt256> stakesCycle = Iterators.cycle(stakes);
		String nodeNamePrefix =  Optional
			.ofNullable(System.getenv("NODE_NAME_PREFIX"))
			.orElse("node");
		return IntStream.range(0, keyDetails.size()).mapToObj(
			n -> {
				String nodeName = String.format("%s%s",nodeNamePrefix, n);
				String stakerKeypath = String.format(STAKER_TEMPLATE, n);
				var validator = keyDetails.get(n);
				validator.setNodeName(nodeName);
				return getStakeDelegation(stakesCycle, stakerKeypath, validator);
			}
		).collect(ImmutableList.toImmutableList());
	}

	private static ImmutableList<KeyDetails> getStakeDelegationUsingExistingKeyList(List<KeyDetails> keyDetails, List<UInt256> stakes) {
		final Iterator<UInt256> stakesCycle = Iterators.cycle(stakes);
		return keyDetails.stream().map(
			keyDetail -> {
				String stakerKeyStore = String.format("%s_staker.ks",keyDetail.getNodeName());
				return getStakeDelegation(stakesCycle, stakerKeyStore, keyDetail);
			}
		).collect(ImmutableList.toImmutableList());
	}

	private static KeyDetails getStakeDelegation(Iterator<UInt256> stakesCycle, String keyStore, KeyDetails validator) {
		try {
			final ECKeyPair stakerKey = Keys.readKey(keyStore,
				"wallet",
				"RADIX_STAKER_KEYSTORE_PASSWORD",
				"RADIX_STAKER_KEY_PASSWORD");
			StakeDelegation stakeDelegation = StakeDelegation.of(stakerKey, validator.getKeyPair().getPublicKey(), stakesCycle.next());
			validator.setStakeDelegation(stakeDelegation);
			validator.setStakerKeyStore(keyStore);
			return validator;
		} catch (CryptoException | IOException e) {
			throw new IllegalStateException("While reading staker keys", e);
		}
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
		List<KeyDetails> keys,
		HelmUniverseOutput helmUniverseOutput,
		AWSSecretsOutputOptions awsSecretsOutputOptions
	) {
		final String VALIDATOR_PREFIX = "VALIDATOR";

		if (isStdOutput(helmUniverseOutput, awsSecretsOutputOptions)) {
			IntStream.range(0, keys.size()).forEach(i -> {
				String keyname = String.format(template, i);
				System.out.format("export RADIXDLT_%s_PRIVKEY=%s%n", keyname, Bytes.toBase64String(keys.get(i).getKeyPair().getPrivateKey()));
			});
		}

		if (isHelmOrAwsOutuput(helmUniverseOutput, awsSecretsOutputOptions)) {
			List<Map<String, Object>> validators = IntStream.range(0,keys.size())
				.mapToObj(i -> {
					var validator = new HashMap<String, Object>();

					validator.put("node",i);
					validator.put("host",keys.get(i).getNodeName());
					validator.put("validatorKeyStore",keys.get(i).getValidatorKeyStore());
					validator.put("stakerKeyStore",keys.get(i).getStakerKeyStore());
					if (template.startsWith(VALIDATOR_PREFIX)) {
						validator.put("seedsRemote", "");
						validator.put("privateKey", Bytes.toBase64String(keys.get(i).getKeyPair().getPrivateKey()));
					} else if (template.startsWith("STAKER")) {
						validator.put("stakingKey", Bytes.toBase64String(keys.get(i).getStakeDelegation().staker().getPrivateKey()));
					}
					return validator;
				})
				.collect(Collectors.toList());

			List<String> nodeNames= validators.stream().map(v -> (String)v.get("host")).collect(Collectors.toList());


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
		UniverseType type,
		Universe u,
		HelmUniverseOutput helmUniverseOutput,
		AWSSecretsOutputOptions awsSecretsOutputOptions
	) {
		final Serialization serialization = DefaultSerialization.getInstance();
		if (!suppressDson) {
			byte[] universeBytes = serialization.toDson(u, Output.WIRE);
			Rri tokenRri = Rri.ofSystem(TokenDefinitionUtils.getNativeTokenShortCode());
			if (isStdOutput(helmUniverseOutput, awsSecretsOutputOptions)) {
				System.out.format("export RADIXDLT_UNIVERSE_TYPE=%s%n", type);
				System.out.format("export RADIXDLT_UNIVERSE_TOKEN=%s%n", tokenRri);
				System.out.format("export RADIXDLT_UNIVERSE=%s%n", Bytes.toBase64String(universeBytes));
			} else if (isHelmOrAwsOutuput(helmUniverseOutput, awsSecretsOutputOptions)) {
				Map<String, Map<String, Object>> config = new HashMap<>();
				Map<String, Object> universe = new HashMap<>();
				universe.put("type", type);
				universe.put("token", tokenRri.toString());

				JSONObject universeJson = new JSONObject(serialization.toJson(u, Output.WIRE));
				universe.put("value", universeJson.toString());

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
			JSONObject json = new JSONObject(serialization.toJson(u, Output.WIRE));
			System.out.println(json.toString(4));
		}
	}

	private static boolean isHelmOrAwsOutuput(HelmUniverseOutput helmUniverseOutput, AWSSecretsOutputOptions awsSecretsOutputOptions) {
		return helmUniverseOutput.getOutputHelmValues() || awsSecretsOutputOptions.getEnableAwsSecrets();
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


	private static void generateValidatorsAWSSecrets(
		final List<Map<String, Object>> validators,
		final String validatorPrefix,
		final String template,
		final AWSSecretsOutputOptions awsSecretsOutputOptions,
		boolean compress
	)  {


		validators.forEach(v -> {
			var validatorAwsSecret = new HashMap<String, Object>();
			String name = (String) v.get("host");
			Object keyData;
			String keyFile,secretName,keyFileSecretName;
			if(template.startsWith("STAKER")){
				 keyData = v.get("stakingKey");
				 keyFile = (String) v.get("stakerKeyStore"); // keyfile name is from the
				 secretName = String.format("%s/%s/staker", awsSecretsOutputOptions.getNetworkName(), name);
				 keyFileSecretName = String.format("%s/%s/staker_key", awsSecretsOutputOptions.getNetworkName(), name);

			}
			else if(template.startsWith(validatorPrefix)){
				 keyData = v.get("privateKey");
				 keyFile = (String) v.get("validatorKeyStore"); // keyfile name is from the
				 secretName = String.format("%s/%s/validator", awsSecretsOutputOptions.getNetworkName(), name);
				 keyFileSecretName = String.format("%s/%s/validator_key", awsSecretsOutputOptions.getNetworkName(), name);
			}
			else {
				throw new IllegalStateException("Invalid template string");
			}
			validatorAwsSecret.put("key",keyData);
			writeTextAWSSecret(validatorAwsSecret, secretName, awsSecretsOutputOptions, compress);

			var keyFileAwsSecret = new HashMap<String, Object>();
			try {
				byte[] data = Files.readAllBytes(Paths.get(keyFile));
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
	KeyDetails(String nodeName, ECKeyPair keyPair,String validatorKeyStore){
		this.nodeName = nodeName;
		this.keyPair = keyPair;
		this.validatorKeyStore = validatorKeyStore;
	}

	public void setStakeDelegation(StakeDelegation stakeDelegation) {
		this.stakeDelegation = stakeDelegation;
	}

	public String getNodeName(){
		return nodeName;
	}
	public void setNodeName(String nodeName){
		this.nodeName = nodeName;
	}

	public ECKeyPair getKeyPair(){
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