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

package com.radixdlt.integration.targeted.staking;

import static com.radixdlt.atom.TxAction.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.modules.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.deterministic.SingleNodeDeterministicRunner;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.keys.LocalSigner;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.REOutput;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.utils.PrivateKeys;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnstakingLockedTokensTest {
  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    return List.of(
        new Object[][] {
          {1, 2, 3, false},
          {1, 2, 4, true},
          {3, 4, 5, false},
          {3, 4, 6, true},
        });
  }

  private static final ECKeyPair TEST_KEY = PrivateKeys.ofNumeric(1);

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Inject @LocalSigner private HashSigner hashSigner;
  @Inject @Self private ECPublicKey self;
  @Inject private SingleNodeDeterministicRunner runner;
  @Inject private RadixEngine<LedgerAndBFTProof> radixEngine;
  @Inject private RadixEngineStateComputer radixEngineStateComputer;
  private final long stakingEpoch;
  private final long unstakingEpoch;
  private final long transferEpoch;
  private final boolean shouldSucceed;

  public UnstakingLockedTokensTest(
      long stakingEpoch, long unstakingEpoch, long transferEpoch, boolean shouldSucceed) {
    if (stakingEpoch < 1) {
      throw new IllegalArgumentException();
    }

    this.stakingEpoch = stakingEpoch;
    this.unstakingEpoch = unstakingEpoch;
    this.transferEpoch = transferEpoch;
    this.shouldSucceed = shouldSucceed;
  }

  private Injector createInjector() {
    return Guice.createInjector(
        MempoolConfig.asModule(1000, 10),
        new MainnetForksModule(),
        new RadixEngineForksLatestOnlyModule(RERulesConfig.testingDefault()),
        new ForksModule(),
        new SingleNodeAndPeersDeterministicNetworkModule(TEST_KEY, 0),
        new MockedGenesisModule(
            Set.of(TEST_KEY.getPublicKey()), Amount.ofTokens(110), Amount.ofTokens(100)),
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant()
                .annotatedWith(DatabaseLocation.class)
                .to(folder.getRoot().getAbsolutePath());
          }
        });
  }

  public REProcessedTxn waitForCommit(AID txnId) {
    var committed =
        runner.runNextEventsThrough(
            LedgerUpdate.class,
            u -> {
              var output = u.getStateComputerOutput().getInstance(REOutput.class);
              return output.getProcessedTxns().stream()
                  .anyMatch(txn -> txn.getTxn().getId().equals(txnId));
            });

    return committed
        .getStateComputerOutput()
        .getInstance(REOutput.class)
        .getProcessedTxns()
        .stream()
        .filter(t -> t.getTxn().getId().equals(txnId))
        .findFirst()
        .orElseThrow();
  }

  public REProcessedTxn dispatchAndWaitForCommit(TxAction action) throws Exception {
    var request = TxnConstructionRequest.create().action(action);
    var txBuilder = radixEngine.construct(request.feePayer(REAddr.ofPubKeyAccount(self)));
    var txn = txBuilder.signAndBuild(hashSigner::sign);
    radixEngineStateComputer.addToMempool(txn);
    return waitForCommit(txn.getId());
  }

  @Test
  public void test_stake_unlocking() throws Exception {
    createInjector().injectMembers(this);

    runner.start();

    if (stakingEpoch > 1) {
      runner.runNextEventsThrough(
          LedgerUpdate.class,
          e -> {
            var epochChange = e.getStateComputerOutput().getInstance(EpochChange.class);
            return epochChange != null && epochChange.getEpoch() == stakingEpoch;
          });
    }

    var accountAddr = REAddr.ofPubKeyAccount(self);
    var stakeTxn =
        dispatchAndWaitForCommit(
            new StakeTokens(accountAddr, self, Amount.ofTokens(10).toSubunits()));
    runner.runNextEventsThrough(
        LedgerUpdate.class,
        e -> {
          var epochChange = e.getStateComputerOutput().getInstance(EpochChange.class);
          return epochChange != null && epochChange.getEpoch() == unstakingEpoch;
        });
    var unstakeTxn =
        dispatchAndWaitForCommit(
            new UnstakeTokens(self, accountAddr, Amount.ofTokens(10).toSubunits()));

    if (transferEpoch > unstakingEpoch) {
      runner.runNextEventsThrough(
          LedgerUpdate.class,
          e -> {
            var epochChange = e.getStateComputerOutput().getInstance(EpochChange.class);
            return epochChange != null && epochChange.getEpoch() == transferEpoch;
          });
    }

    var otherAddr = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
    var transferAction =
        new TransferToken(
            REAddr.ofNativeToken(), accountAddr, otherAddr, Amount.ofTokens(10).toSubunits());

    // Build transaction through radix engine
    if (shouldSucceed) {
      radixEngine.construct(transferAction);
    } else {
      assertThatThrownBy(() -> radixEngine.construct(transferAction))
          .isInstanceOf(TxBuilderException.class);
    }
  }
}
