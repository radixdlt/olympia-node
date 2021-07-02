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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.radixdlt.CryptoModule;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.GenesisProvider;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.security.Security;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class GenerateUniverses {
	private GenerateUniverses() { }

	private static final String DEFAULT_TIMESTAMP = String.valueOf(Instant.parse("2020-01-01T00:00:00.00Z").getEpochSecond());
	private static final UInt256 DEFAULT_ISSUANCE = Amount.ofTokens(1000000000).toSubunits(); // 1 Billion!
	private static final UInt256 DEFAULT_STAKE = Amount.ofTokens(100).toSubunits();
	private static final String mnemomicKeyHex = "022873d192f4186907fbc725109d5679c983a167a4a06768ef7922664a5fc31fe2";


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

		var allValidatorKeysBuilder = ImmutableList.<ECPublicKey>builder();
		if (cmd.getOptionValue("p") != null) {
			var hexKeys = cmd.getOptionValue("p").split(",");
			for (var hexKey : hexKeys) {
				allValidatorKeysBuilder.add(ECPublicKey.fromHex(hexKey));
			}
		}
		final int validatorsCount = cmd.getOptionValue("v") != null ? Integer.parseInt(cmd.getOptionValue("v")) : 0;
		var nextGeneratedKey = new AtomicInteger(6);
		var generatedValidatorKeys = IntStream.generate(() -> nextGeneratedKey.getAndAdd(1))
			.mapToObj(GenerateUniverses::privKeyOf)
			.limit(validatorsCount)
			.collect(Collectors.toList());
		generatedValidatorKeys.stream().map(ECKeyPair::getPublicKey).forEach(allValidatorKeysBuilder::add);
		var allValidatorKeys = allValidatorKeysBuilder.build();


		// Issuances to mnemomic account, keys 1-5, and 1st validator
		final var mnemomicKey = ECPublicKey.fromHex(mnemomicKeyHex);
		final ImmutableList.Builder<TokenIssuance> tokenIssuancesBuilder = ImmutableList.builder();
		tokenIssuancesBuilder.add(TokenIssuance.of(mnemomicKey, DEFAULT_ISSUANCE));
		tokenIssuancesBuilder.add(
			TokenIssuance.of(pubkeyOf(1), DEFAULT_ISSUANCE),
			TokenIssuance.of(pubkeyOf(2), DEFAULT_ISSUANCE),
			TokenIssuance.of(pubkeyOf(3), DEFAULT_ISSUANCE),
			TokenIssuance.of(pubkeyOf(4), DEFAULT_ISSUANCE),
			TokenIssuance.of(pubkeyOf(5), DEFAULT_ISSUANCE)
		);
		// Issue tokens to initial validators for now to support application services
		allValidatorKeys.forEach(pk -> tokenIssuancesBuilder.add(TokenIssuance.of(pk, DEFAULT_ISSUANCE)));

		// Stakes issued by mnemomic account
		var stakes = allValidatorKeys.stream()
			.map(pk -> new StakeTokens(REAddr.ofPubKeyAccount(mnemomicKey), pk, DEFAULT_STAKE))
			.collect(Collectors.toSet());

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
				bind(new TypeLiteral<List<TxAction>>() {}).annotatedWith(Genesis.class).toInstance(List.of());
				bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
				bind(SystemCounters.class).toInstance(new SystemCountersImpl());
				bindConstant().annotatedWith(Genesis.class).to(DEFAULT_TIMESTAMP);
				bind(new TypeLiteral<Set<StakeTokens>>() { }).annotatedWith(Genesis.class).toInstance(stakes);
				bind(new TypeLiteral<ImmutableList<TokenIssuance>>() {}).annotatedWith(Genesis.class)
					.toInstance(tokenIssuancesBuilder.build());
				bind(new TypeLiteral<ImmutableList<ECPublicKey>>() {}).annotatedWith(Genesis.class)
					.toInstance(allValidatorKeys);
				bindConstant().annotatedWith(MaxValidators.class).to(100);
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

	private static ECKeyPair privKeyOf(int pk) {
		var privateKey = new byte[ECKeyPair.BYTES];
		Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);

		try {
			return ECKeyPair.fromPrivateKey(privateKey);
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalArgumentException("Error while generating public key", e);
		}
	}

	private static ECPublicKey pubkeyOf(int pk) {
		return privKeyOf(pk).getPublicKey();
	}

	private static void usage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(GenerateUniverses.class.getSimpleName(), options, true);
	}
}