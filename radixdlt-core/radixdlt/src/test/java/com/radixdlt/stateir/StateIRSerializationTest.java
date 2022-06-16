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

package com.radixdlt.stateir;

import static com.radixdlt.atom.TxAction.CreateMutableToken;
import static org.junit.Assert.assertEquals;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.hotstuff.LedgerProof;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.modules.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.statecomputer.forks.modules.MainnetForksModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt256;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class StateIRSerializationTest {
  private static final ECKeyPair VALIDATOR_KEY = PrivateKeys.ofNumeric(1);

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Inject private RadixEngine<LedgerAndBFTProof> radixEngine;

  @Inject private BerkeleyLedgerEntryStore ledgerEntryStore;

  // FIXME: Hack, need this in order to cause provider for genesis to be stored
  @Inject @LastStoredProof private LedgerProof ledgerProof;

  @Inject private Forks forks;

  private final Random rnd = new Random();

  private Injector createInjector() {
    return Guice.createInjector(
        MempoolConfig.asModule(1000, 10),
        new MainnetForksModule(),
        new RadixEngineForksLatestOnlyModule(
            RERulesConfig.testingDefault()
                .overrideFeeTable(
                    FeeTable.create(
                        Amount.zero(), Map.of(TokenResource.class, Amount.ofTokens(1))))),
        new ForksModule(),
        new SingleNodeAndPeersDeterministicNetworkModule(VALIDATOR_KEY, 0),
        new MockedGenesisModule(
            Set.of(VALIDATOR_KEY.getPublicKey()),
            Amount.ofTokens(100_000_000_000L),
            Amount.ofTokens(100L)),
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant()
                .annotatedWith(DatabaseLocation.class)
                .to(folder.getRoot().getAbsolutePath());
          }
        });
  }

  @Test
  public void
      it_should_successfully_construct_and_then_serialize_and_deserialize_the_intermediate_state()
          throws Exception {
    createInjector().injectMembers(this);

    final var numAccounts = 100;
    final var numTokens = 10;
    final var transfersPerToken = 100;

    final var accounts =
        IntStream.range(0, numAccounts)
            .mapToObj(unused -> REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey()))
            .toList();

    for (REAddr accountAddr : accounts) {
      transfer1Rad(VALIDATOR_KEY, accountAddr);
    }

    for (int i = 0; i < numTokens; i++) {
      final var tokenAddr = createMutableToken(VALIDATOR_KEY, UInt256.from(10_000_000_000L));
      final var accountsForTransfer = new ArrayList<REAddr>();
      for (int j = 0; j < transfersPerToken; j++) {
        accountsForTransfer.add(accounts.get(rnd.nextInt(accounts.size())));
      }

      transferRandomAmtOfTokensToAccounts(VALIDATOR_KEY, tokenAddr, accountsForTransfer, 1000);
    }

    final var substateDeserialization =
        forks.newestFork().engineRules().parser().getSubstateDeserialization();

    final var state =
        ledgerEntryStore.transaction(
            tx -> new StateIRConstructor(tx, substateDeserialization).prepareOlympiaStateIR());

    final var serialized = OlympiaStateIRSerializer.serialize(state);

    try (final var bais = new ByteArrayInputStream(serialized)) {
      final var deserialized = new OlympiaStateIRDeserializer(bais).deserialize();
      assertEquals(deserialized, state);
    }
  }

  private REAddr createMutableToken(ECKeyPair sender, UInt256 amount)
      throws TxBuilderException, RadixEngineException {
    final var senderAcc = REAddr.ofPubKeyAccount(sender.getPublicKey());

    final var tokenDef =
        new MutableTokenDefinition(
            sender.getPublicKey(),
            randomLowercaseAtoZ(10),
            randomLowercaseAtoZ(10),
            randomLowercaseAtoZ(10),
            null,
            null);

    final var tokenAddr = REAddr.ofHashedKey(sender.getPublicKey(), tokenDef.getSymbol());

    final var txn =
        radixEngine
            .construct(
                TxnConstructionRequest.create()
                    .feePayer(senderAcc)
                    .action(createMutableTokenActionFromTokenDef(tokenDef))
                    .mint(tokenAddr, senderAcc, amount))
            .signAndBuild(sender::sign);

    radixEngine.execute(List.of(txn));

    return tokenAddr;
  }

  private void transfer1Rad(ECKeyPair sender, REAddr receiverAddr)
      throws TxBuilderException, RadixEngineException {
    final var senderAddr = REAddr.ofPubKeyAccount(sender.getPublicKey());
    final var txn =
        radixEngine
            .construct(
                TxnConstructionRequest.create()
                    .feePayer(senderAddr)
                    .transfer(REAddr.ofNativeToken(), senderAddr, receiverAddr, UInt256.ONE))
            .signAndBuild(sender::sign);

    radixEngine.execute(List.of(txn));
  }

  private void transferRandomAmtOfTokensToAccounts(
      ECKeyPair sender, REAddr tokenAddr, List<REAddr> accounts, int maxAmount)
      throws TxBuilderException, RadixEngineException {
    final var senderAddr = REAddr.ofPubKeyAccount(sender.getPublicKey());
    final var actions =
        accounts.stream()
            .<TxAction>map(
                account -> {
                  final var amount = UInt256.from(rnd.nextInt(maxAmount) + 1);
                  return new TxAction.TransferToken(tokenAddr, senderAddr, account, amount);
                })
            .toList();

    final var txn =
        radixEngine
            .construct(TxnConstructionRequest.create().feePayer(senderAddr).actions(actions))
            .signAndBuild(sender::sign);

    radixEngine.execute(List.of(txn));
  }

  private static CreateMutableToken createMutableTokenActionFromTokenDef(
      MutableTokenDefinition def) {
    return new CreateMutableToken(
        def.getResourceAddress(),
        def.getSymbol(),
        def.getName(),
        def.getDescription(),
        def.getIconUrl(),
        def.getTokenUrl(),
        def.getOwner());
  }

  private String randomLowercaseAtoZ(int len) {
    final int leftLimit = 97; // letter 'a'
    final int rightLimit = 122; // letter 'z'

    return rnd.ints(leftLimit, rightLimit + 1)
        .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
        .limit(len)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
  }
}
