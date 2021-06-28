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
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERules;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import com.radixdlt.CryptoModule;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.NodeAddressing;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.GenesisProvider;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.security.Security;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class GenerateUniverses {
	private GenerateUniverses() { }

	private static final String DEFAULT_TIMESTAMP = String.valueOf(Instant.parse("2020-01-01T00:00:00.00Z").getEpochSecond());
	private static final UInt256 DEFAULT_ISSUANCE = UInt256.from("1000000000000000000000000000").multiply(TokenDefinitionUtils.SUB_UNITS);

	public static void main(String[] args) throws Exception {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);

		Options options = new Options();
		options.addOption("h", "help",                   false, "Show usage information (this message)");
		options.addOption("p", "public-keys",        true,  "Specify validator keys");
		options.addOption("v", "validator-count",        true,  "Specify number of validators to generate");

		CommandLineParser parser = new DefaultParser();
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

		final int validatorsCount = cmd.getOptionValue("v") != null ? Integer.parseInt(cmd.getOptionValue("v")) : 0;
		var generatedValidatorKeys = Stream.generate(ECKeyPair::generateNew).limit(validatorsCount).collect(Collectors.toList());

		var validatorKeys = new ArrayList<ECPublicKey>();
		if (cmd.getOptionValue("p") != null) {
			var hexKeys = cmd.getOptionValue("p").split(",");
			for (var hexKey : hexKeys) {
				validatorKeys.add(ECPublicKey.fromHex(hexKey));
			}
		}

		final ImmutableList.Builder<TokenIssuance> tokenIssuancesBuilder = ImmutableList.builder();
		tokenIssuancesBuilder.add(
			TokenIssuance.of(pubkeyOf(1), DEFAULT_ISSUANCE),
			TokenIssuance.of(pubkeyOf(2), DEFAULT_ISSUANCE),
			TokenIssuance.of(pubkeyOf(3), DEFAULT_ISSUANCE),
			TokenIssuance.of(pubkeyOf(4), DEFAULT_ISSUANCE),
			TokenIssuance.of(pubkeyOf(5), DEFAULT_ISSUANCE)
		);

		var allValidatorKeysBuilder = ImmutableList.<ECPublicKey>builder().addAll(validatorKeys);
		generatedValidatorKeys.stream().map(ECKeyPair::getPublicKey).forEach(allValidatorKeysBuilder::add);
		var allValidatorKeys = allValidatorKeysBuilder.build();

		var additionalActions = new ArrayList<TxAction>();
		// Issue tokens to initial validators for now to support application services
		allValidatorKeys.forEach(pk -> {
			var tokenIssuance = TokenIssuance.of(pk, DEFAULT_ISSUANCE);
			tokenIssuancesBuilder.add(tokenIssuance);
			additionalActions.add(new StakeTokens(REAddr.ofPubKeyAccount(pk), pk, DEFAULT_ISSUANCE));
		});

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
				bindConstant().annotatedWith(Genesis.class).to(DEFAULT_TIMESTAMP);
				bind(new TypeLiteral<ImmutableList<TokenIssuance>>() {}).annotatedWith(Genesis.class)
					.toInstance(tokenIssuancesBuilder.build());
				bind(new TypeLiteral<ImmutableList<StakeDelegation>>() {}).annotatedWith(Genesis.class)
					.toInstance(ImmutableList.of());
				bind(new TypeLiteral<ImmutableList<ECPublicKey>>() {}).annotatedWith(Genesis.class)
					.toInstance(allValidatorKeys);
			}
		}).getInstance(GenesisProvider.class);

		var genesis = genesisProvider.get().getTxns().get(0);
		IntStream.range(0, generatedValidatorKeys.size()).forEach(i -> {
			System.out.format("export RADIXDLT_VALIDATOR_%s_PRIVKEY=%s%n", i, Bytes.toBase64String(generatedValidatorKeys.get(i).getPrivateKey()));
			System.out.format("export RADIXDLT_VALIDATOR_%s_PUBKEY=%s%n", i, Addressing.ofNetwork(Network.LOCALNET).forNodes().of(generatedValidatorKeys.get(i).getPublicKey()));
		});
		if (validatorsCount > 0) {
			System.out.format("export RADIXDLT_GENESIS_TXN=%s%n", Bytes.toHexString(genesis.getPayload()));
		} else {
			try (var writer = new BufferedWriter(new FileWriter("genesis.json"))) {
				writer.write(new JSONObject().put("genesis", Bytes.toHexString(genesis.getPayload())).toString());
			}
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

	private static void usage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(GenerateUniverses.class.getSimpleName(), options, true);
	}
}