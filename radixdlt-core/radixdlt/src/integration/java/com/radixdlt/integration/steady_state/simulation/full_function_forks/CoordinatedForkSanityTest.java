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

package com.radixdlt.integration.steady_state.simulation.full_function_forks;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.inject.Key;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.harness.simulation.NetworkLatencies;
import com.radixdlt.harness.simulation.NetworkOrdering;
import com.radixdlt.harness.simulation.SimulationTest;
import com.radixdlt.harness.simulation.SimulationTest.Builder;
import com.radixdlt.harness.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.harness.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.harness.simulation.monitors.radix_engine.RadixEngineMonitors;
import com.radixdlt.harness.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.forks.CandidateForkVote;
import com.radixdlt.statecomputer.forks.CurrentForkView;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.ForksEpochStore;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.utils.UInt256;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Assert;
import org.junit.Test;

public final class CoordinatedForkSanityTest {
  private static final int NUM_VALIDATORS = 4;
  private final Builder bftTestBuilder;

  public CoordinatedForkSanityTest() {
    bftTestBuilder =
        SimulationTest.builder()
            .numNodes(NUM_VALIDATORS, Collections.nCopies(NUM_VALIDATORS, UInt256.ONE))
            .networkModules(NetworkOrdering.inOrder(), NetworkLatencies.fixed())
            .fullFunctionNodes(SyncConfig.of(400L, 10, 2000L))
            .addRadixEngineConfigModules(new MockedForksModule(2), new ForksModule())
            .addNodeModule(MempoolConfig.asModule(1000, 10))
            .addTestModules(
                ConsensusMonitors.safety(),
                ConsensusMonitors.liveness(1, TimeUnit.SECONDS),
                ConsensusMonitors.noTimeouts(),
                ConsensusMonitors.directParents(),
                LedgerMonitors.consensusToLedger(),
                LedgerMonitors.ordered(),
                RadixEngineMonitors.noInvalidProposedCommands());
  }

  @Test
  public void sanity_tests_should_pass() {
    final var simulationTest = bftTestBuilder.build();
    final var runningTest = simulationTest.run(Duration.ofSeconds(30));
    final var network = runningTest.getNetwork();
    final var nodes = ImmutableList.copyOf(network.getNodes());
    final var halfOfTheNodes = nodes.subList(0, nodes.size() / 2);
    final var oneMoreNode = nodes.get(nodes.size() / 2);

    final var forks = network.getInstance(Forks.class, nodes.get(0)).forkConfigs();

    final var firstError = new AtomicReference<String>();
    final Consumer<String> reportError =
        err -> {
          if (firstError.get() == null) {
            firstError.set(err);
          }
        };

    final var latestEpochChange = new AtomicReference<EpochChange>();
    final var testErrorsDisposable =
        network
            .latestEpochChanges()
            .subscribe(
                epochChange -> {
                  latestEpochChange.set(epochChange);

                  // just a sanity check that all validators have the same power (this test depends
                  // on this assumption)
                  final var validatorSet = epochChange.getBFTConfiguration().getValidatorSet();
                  final var validatorPower =
                      validatorSet.getValidators().iterator().next().getPower();
                  final var allValidatorsHaveTheSamePower =
                      validatorSet.getValidators().stream()
                          .allMatch(v -> v.getPower().equals(validatorPower));

                  if (!allValidatorsHaveTheSamePower) {
                    reportError.accept("Expected all validators to have the same power");
                  }

                  if (epochChange.getEpoch() == 10L) {
                    // half nodes vote in epoch 10
                    halfOfTheNodes.forEach(node -> updateValidatorWithNewestFork(network, node));
                  } else if (epochChange.getEpoch() == 11L) {
                    // verify that at epoch 11 the network is still at fork idx 2
                    if (!verifyCurrentFork(network, forks.get(2))) {
                      reportError.accept("Expected to be at a different fork (2) at epoch 11");
                    }
                  } else if (epochChange.getEpoch() == 12L) {
                    // verify that at epoch 12 the network is still at fork idx 2
                    if (!verifyCurrentFork(network, forks.get(2))) {
                      reportError.accept("Expected to be at a different fork (2) at epoch 12");
                    }

                    // one more node votes in epoch 12
                    updateValidatorWithNewestFork(network, oneMoreNode);
                  } else if (epochChange.getEpoch() == 19L) {
                    // still no change at epoch 19 (min epoch is 20)
                    if (!verifyCurrentFork(network, forks.get(2))) {
                      reportError.accept("Expected to be at a different fork (2) at epoch 19");
                    }
                  } else if (epochChange.getEpoch() == 21L) {
                    // verify that at epoch 21 we've successfully switched to the fork with voting
                    // (idx 3)
                    if (!verifyCurrentFork(network, forks.get(3))) {
                      reportError.accept("Expected to be at a different fork (3) at epoch 21");
                    }

                    // verify that the nodes have actually forked at epoch 20
                    final var allForkedAtEpoch20 =
                        network.getNodes().stream()
                            .allMatch(
                                node ->
                                    network
                                        .getInstance(ForksEpochStore.class, node)
                                        .getStoredForks()
                                        .get(20L)
                                        .equals(forks.get(3).name()));
                    if (!allForkedAtEpoch20) {
                      reportError.accept("All validators should have forked at epoch 20");
                    }
                  }
                });

    final var results = runningTest.awaitCompletion();

    if (firstError.get() != null) {
      Assert.fail(firstError.get());
    }
    testErrorsDisposable.dispose();

    // make sure that at least 20 epochs have passed (fork min epoch)
    assertTrue(latestEpochChange.get().getEpoch() > 20);

    // verify that at the end of test all nodes are at fork idx 3
    if (!verifyCurrentFork(network, forks.get(3))) {
      throw new IllegalStateException("Expected to be at a different fork (3) at the end of test");
    }

    assertThat(results)
        .allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
  }

  private void updateValidatorWithNewestFork(RunningNetwork network, BFTNode node) {
    final var forks = network.getInstance(Forks.class, node);
    final var forkVoteOpt =
        forks.getCandidateFork().map(f -> CandidateForkVote.create(node.getKey(), f));
    final var keyPair = network.getInstance(ECKeyPair.class, node);
    try {
      final var txRequest =
          network
              .getInstance(new Key<RadixEngine<LedgerAndBFTProof>>() {}, node)
              .construct(
                  TxnConstructionRequest.create()
                      .updateValidatorSystemMetadata(
                          node.getKey(),
                          forkVoteOpt
                              .map(CandidateForkVote::payload)
                              .orElseGet(HashUtils::zero256)))
              .signAndBuild(keyPair::sign);
      network.getInstance(RadixEngineStateComputer.class, node).addToMempool(txRequest);
    } catch (TxBuilderException | MempoolRejectedException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean verifyCurrentFork(RunningNetwork network, ForkConfig forkConfig) {
    return network.getNodes().stream()
        .allMatch(
            node -> {
              final var nodeFork =
                  network.getInstance(CurrentForkView.class, node).currentForkConfig();
              return nodeFork.name().equals(forkConfig.name());
            });
  }
}
