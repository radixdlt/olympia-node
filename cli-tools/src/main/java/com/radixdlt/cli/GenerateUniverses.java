/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.cli;

import static com.radixdlt.atom.TxAction.*;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.OptionalBinder;
import com.radixdlt.modules.CryptoModule;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.TxAction;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.GenesisProvider;
import com.radixdlt.statecomputer.forks.CurrentForkView;
import com.radixdlt.statecomputer.forks.ForkBuilder;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.checkpoint.TokenIssuance;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.NewestForkConfig;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt256;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.security.Security;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;

public final class GenerateUniverses {
  private GenerateUniverses() {}

  private static final UInt256 DEFAULT_ISSUANCE =
      Amount.ofTokens(1000000000).toSubunits(); // 1 Billion!
  private static final UInt256 DEFAULT_STAKE = Amount.ofTokens(100).toSubunits();
  private static final String mnemomicKeyHex =
      "0236856ea9fa8c243e45fc94ec27c29cf3f17e3a9e19a410ee4a41f4858e379918";

  public static void main(String[] args) throws Exception {
    Security.insertProviderAt(new BouncyCastleProvider(), 1);

    Options options = new Options();
    options.addOption("h", "help", false, "Show usage information (this message)");
    options.addOption("p", "public-keys", true, "Specify validator keys");
    options.addOption("v", "validator-count", true, "Specify number of validators to generate");

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

    var validatorKeys = new HashSet<ECPublicKey>();
    if (cmd.getOptionValue("p") != null) {
      var hexKeys = cmd.getOptionValue("p").split(",");
      for (var hexKey : hexKeys) {
        validatorKeys.add(ECPublicKey.fromHex(hexKey));
      }
    }
    final int validatorsCount =
        cmd.getOptionValue("v") != null ? Integer.parseInt(cmd.getOptionValue("v")) : 0;
    var generatedValidatorKeys = PrivateKeys.numeric(6).limit(validatorsCount).toList();
    generatedValidatorKeys.stream().map(ECKeyPair::getPublicKey).forEach(validatorKeys::add);

    // Issuances to mnemomic account, keys 1-5, and 1st validator
    final var mnemomicKey = ECPublicKey.fromHex(mnemomicKeyHex);
    final ImmutableList.Builder<TokenIssuance> tokenIssuancesBuilder = ImmutableList.builder();
    tokenIssuancesBuilder.add(TokenIssuance.of(mnemomicKey, DEFAULT_ISSUANCE));
    PrivateKeys.numeric(1)
        .limit(5)
        .map(k -> TokenIssuance.of(k.getPublicKey(), DEFAULT_ISSUANCE))
        .forEach(tokenIssuancesBuilder::add);
    // Issue tokens to initial validators for now to support application services
    validatorKeys.forEach(pk -> tokenIssuancesBuilder.add(TokenIssuance.of(pk, DEFAULT_ISSUANCE)));

    // Stakes issued by mnemomic account
    var stakes =
        validatorKeys.stream()
            .map(pk -> new StakeTokens(REAddr.ofPubKeyAccount(mnemomicKey), pk, DEFAULT_STAKE))
            .collect(Collectors.toSet());

    var timestamp = String.valueOf(Instant.now().getEpochSecond());

    var genesisProvider =
        Guice.createInjector(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    install(new CryptoModule());
                    install(
                        new AbstractModule() {
                          @Provides
                          @Singleton
                          private Forks forks(Set<ForkBuilder> forkBuilders) {
                            return Forks.create(
                                forkBuilders.stream()
                                    .map(ForkBuilder::build)
                                    .collect(Collectors.toSet()));
                          }

                          @Provides
                          @Singleton
                          private CurrentForkView currentForkView(Forks forks) {
                            return new CurrentForkView(forks, forks.genesisFork());
                          }

                          @Provides
                          @Singleton
                          @NewestForkConfig
                          private ForkConfig newestForkConfig(Forks forks) {
                            return forks.newestFork();
                          }
                        });
                    install(new MainnetForksModule());
                    bind(new TypeLiteral<List<TxAction>>() {})
                        .annotatedWith(Genesis.class)
                        .toInstance(List.of());
                    bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
                    bind(SystemCounters.class).toInstance(new SystemCountersImpl());
                    bindConstant().annotatedWith(Genesis.class).to(timestamp);
                    bind(new TypeLiteral<Set<StakeTokens>>() {})
                        .annotatedWith(Genesis.class)
                        .toInstance(stakes);
                    bind(new TypeLiteral<ImmutableList<TokenIssuance>>() {})
                        .annotatedWith(Genesis.class)
                        .toInstance(tokenIssuancesBuilder.build());
                    bind(new TypeLiteral<Set<ECPublicKey>>() {})
                        .annotatedWith(Genesis.class)
                        .toInstance(validatorKeys);
                    bindConstant().annotatedWith(MaxValidators.class).to(100);
                    OptionalBinder.newOptionalBinder(
                        binder(), Key.get(new TypeLiteral<List<TxAction>>() {}, Genesis.class));
                  }
                })
            .getInstance(GenesisProvider.class);

    var genesis = genesisProvider.get().getTxns().get(0);
    IntStream.range(0, generatedValidatorKeys.size())
        .forEach(
            i -> {
              System.out.format(
                  "export RADIXDLT_VALIDATOR_%s_PRIVKEY=%s%n",
                  i, Bytes.toBase64String(generatedValidatorKeys.get(i).getPrivateKey()));
              System.out.format(
                  "export RADIXDLT_VALIDATOR_%s_PUBKEY=%s%n",
                  i,
                  Addressing.ofNetwork(Network.LOCALNET)
                      .forNodes()
                      .of(generatedValidatorKeys.get(i).getPublicKey()));
            });
    if (validatorsCount > 0) {
      System.out.format(
          "export RADIXDLT_GENESIS_TXN=%s%n", Bytes.toHexString(genesis.getPayload()));
    } else {
      try (var writer = new BufferedWriter(new FileWriter("genesis.json"))) {
        writer.write(
            new JSONObject().put("genesis", Bytes.toHexString(genesis.getPayload())).toString());
      }
    }
  }

  private static void usage(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(GenerateUniverses.class.getSimpleName(), options, true);
  }
}
