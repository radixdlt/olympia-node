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

package com.radixdlt.integration.targeted.statemachine;

import static com.radixdlt.atom.TxAction.*;
import static com.radixdlt.constraintmachine.REInstruction.REMicroOp.MSG;

import com.google.common.base.Stopwatch;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.modules.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.REEvent;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.integration.Slow;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt256;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

@Category(Slow.class)
public class LargeEpochChangeTest {
  private static final int NUM_ROUNDS = 10000;
  private static final Logger logger = LogManager.getLogger();
  private static final ECKeyPair TEST_KEY = PrivateKeys.ofNumeric(1);
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Inject private RadixEngine<LedgerAndBFTProof> sut;

  // FIXME: Hack, need this in order to cause provider for genesis to be stored
  @Inject @LastStoredProof private LedgerProof ledgerProof;

  private Injector createInjector() {
    return Guice.createInjector(
        MempoolConfig.asModule(1000, 10),
        new MainnetForksModule(),
        new RadixEngineForksLatestOnlyModule(
            new RERulesConfig(
                Set.of("xrd"),
                Pattern.compile("[a-z0-9]+"),
                FeeTable.create(
                    Amount.ofMicroTokens(200), // 0.0002XRD per byte fee
                    Map.of(
                        TokenResource.class, Amount.ofTokens(1000), // 1000XRD per resource
                        ValidatorRegisteredCopy.class,
                            Amount.ofTokens(5), // 5XRD per validator update
                        ValidatorFeeCopy.class, Amount.ofTokens(5), // 5XRD per register update
                        ValidatorOwnerCopy.class, Amount.ofTokens(5), // 5XRD per register update
                        ValidatorMetaData.class, Amount.ofTokens(5), // 5XRD per register update
                        AllowDelegationFlag.class, Amount.ofTokens(5), // 5XRD per register update
                        PreparedStake.class, Amount.ofMilliTokens(500), // 0.5XRD per stake
                        PreparedUnstakeOwnership.class,
                            Amount.ofMilliTokens(500) // 0.5XRD per unstake
                        )),
                1024 * 1024,
                OptionalInt.of(50), // 50 Txns per round
                NUM_ROUNDS,
                1,
                Amount.ofTokens(100), // Minimum stake
                150, // Two weeks worth of epochs
                Amount.ofTokens(10), // Rewards per proposal
                9800, // 98.00% threshold for completed proposals to get any rewards
                100, // 100 max validators
                MSG.maxLength())),
        new ForksModule(),
        new SingleNodeAndPeersDeterministicNetworkModule(TEST_KEY, 0),
        new MockedGenesisModule(
            Set.of(TEST_KEY.getPublicKey()), Amount.ofTokens(100000), Amount.ofTokens(1000)),
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
  public void large_epoch() throws Exception {
    var rt = Runtime.getRuntime();
    logger.info("max mem: {}MB", rt.maxMemory() / 1024 / 1024);

    int privKeyStart = 2;
    int numTxnsPerRound = 10;

    createInjector().injectMembers(this);
    // Arrange
    var request = TxnConstructionRequest.create();
    IntStream.range(privKeyStart, NUM_ROUNDS * numTxnsPerRound + privKeyStart)
        .forEach(
            i -> {
              var k = PrivateKeys.ofNumeric(i);
              var addr = REAddr.ofPubKeyAccount(k.getPublicKey());
              request.action(
                  new MintToken(
                      REAddr.ofNativeToken(),
                      addr,
                      Amount.ofTokens(NUM_ROUNDS * 1000).toSubunits()));
              request.action(new RegisterValidator(k.getPublicKey()));
            });
    var mint = sut.construct(request).buildWithoutSignature();
    logger.info("mint_txn_size={}", mint.getPayload().length);
    var accumulator = new AccumulatorState(2, HashUtils.zero256());
    var proof =
        new LedgerProof(
            HashUtils.zero256(),
            LedgerHeader.create(1, View.of(1), accumulator, 0),
            new TimestampedECDSASignatures());
    sut.execute(List.of(mint), LedgerAndBFTProof.create(proof), PermissionLevel.SYSTEM);

    var systemConstruction = Stopwatch.createUnstarted();
    var construction = Stopwatch.createUnstarted();
    var signatures = Stopwatch.createUnstarted();
    var execution = Stopwatch.createUnstarted();

    var feesPaid = UInt256.ZERO;

    for (int round = 1; round <= NUM_ROUNDS; round++) {
      if (round % NUM_ROUNDS == 0) {
        logger.info(
            "Staking txn {}/{} sys_construct_time: {}s user_construct_time: {}s sig_time: {}s"
                + " execute_time: {}s",
            round * (numTxnsPerRound + 1),
            NUM_ROUNDS * (numTxnsPerRound + 1),
            systemConstruction.elapsed(TimeUnit.SECONDS),
            construction.elapsed(TimeUnit.SECONDS),
            signatures.elapsed(TimeUnit.SECONDS),
            execution.elapsed(TimeUnit.SECONDS));
      }
      var txns = new ArrayList<Txn>();
      systemConstruction.start();
      var sysTxn =
          sut.construct(new NextRound(round, false, 1, v -> TEST_KEY.getPublicKey()))
              .buildWithoutSignature();
      systemConstruction.stop();
      txns.add(sysTxn);
      for (int i = 0; i < numTxnsPerRound; i++) {
        var privateKey = PrivateKeys.ofNumeric((round - 1) * numTxnsPerRound + i + privKeyStart);
        var pubKey = privateKey.getPublicKey();
        var addr = REAddr.ofPubKeyAccount(privateKey.getPublicKey());
        construction.start();
        var builder =
            sut.construct(
                TxnConstructionRequest.create()
                    .feePayer(addr)
                    .action(new StakeTokens(addr, pubKey, Amount.ofTokens(100 + i).toSubunits())));
        construction.stop();
        signatures.start();
        var txn = builder.signAndBuild(privateKey::sign);
        signatures.stop();
        txns.add(txn);
      }

      var acc = new AccumulatorState(2 + round * (numTxnsPerRound + 1), HashUtils.zero256());
      var proof2 =
          new LedgerProof(
              HashUtils.zero256(),
              LedgerHeader.create(1, View.of(1), acc, 0),
              new TimestampedECDSASignatures());
      execution.start();
      var result = sut.execute(txns, LedgerAndBFTProof.create(proof2), PermissionLevel.SUPER_USER);
      execution.stop();
      for (var p : result.getProcessedTxns()) {
        feesPaid = feesPaid.add(p.getFeePaid());
      }
    }
    logger.info("total_fees_paid: {}", Amount.ofSubunits(feesPaid));

    // Act
    construction.reset();
    construction.start();
    logger.info("constructing epoch...");
    var txn = sut.construct(new NextEpoch(1)).buildWithoutSignature();
    construction.stop();
    logger.info(
        "epoch_construction: size={}MB time={}s",
        txn.getPayload().length / 1024 / 1024,
        construction.elapsed(TimeUnit.SECONDS));

    construction.reset();
    construction.start();
    logger.info("preparing epoch...");
    var result = sut.transientBranch().execute(List.of(txn), PermissionLevel.SUPER_USER);
    sut.deleteBranches();
    var nextValidatorSet =
        result.getProcessedTxn().getEvents().stream()
            .filter(REEvent.NextValidatorSetEvent.class::isInstance)
            .map(REEvent.NextValidatorSetEvent.class::cast)
            .findFirst()
            .map(
                e ->
                    BFTValidatorSet.from(
                        e.nextValidators().stream()
                            .map(
                                v ->
                                    BFTValidator.from(
                                        BFTNode.create(v.validatorKey()), v.amount()))));
    var stateUpdates = result.getProcessedTxn().stateUpdates().count();
    construction.stop();
    logger.info(
        "epoch_preparation: state_updates={} verification_time={}s store_time={}s total_time={}s",
        stateUpdates,
        result.getVerificationTime() / 1000,
        result.getStoreTime() / 1000,
        construction.elapsed(TimeUnit.SECONDS));
    construction.reset();
    construction.start();
    logger.info("executing epoch...");
    var acc = new AccumulatorState(2 + 1 + NUM_ROUNDS * (1 + numTxnsPerRound), HashUtils.zero256());
    var header = LedgerHeader.create(1, View.of(10), acc, 0, nextValidatorSet.orElseThrow());
    var proof2 = new LedgerProof(HashUtils.zero256(), header, new TimestampedECDSASignatures());
    var executionResult =
        this.sut.execute(
            List.of(txn), LedgerAndBFTProof.create(proof2), PermissionLevel.SUPER_USER);
    construction.stop();
    logger.info(
        "epoch_execution: verification_time={}s store_time={}s total_time={}s",
        executionResult.getVerificationTime() / 1000,
        executionResult.getStoreTime() / 1000,
        construction.elapsed(TimeUnit.SECONDS));
    for (var v : nextValidatorSet.orElseThrow().getValidators()) {
      logger.info("validator {} {}", v.getNode(), v.getPower());
    }
  }
}
