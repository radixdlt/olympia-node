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

import static java.util.function.Predicate.not;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.OptionalBinder;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.harness.simulation.NetworkLatencies;
import com.radixdlt.harness.simulation.NetworkOrdering;
import com.radixdlt.harness.simulation.SimulationTest;
import com.radixdlt.harness.simulation.SimulationTest.Builder;
import com.radixdlt.harness.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.harness.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.harness.simulation.monitors.radix_engine.RadixEngineMonitors;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.forks.ForkBuilder;
import com.radixdlt.statecomputer.forks.ForksEpochStore;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksGenesisOnlyModule;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.utils.UInt256;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Assert;
import org.junit.Test;

public final class OutdatedNodeForksTest {
  private final Builder bftTestBuilder;

  private final int numValidators = 4;

  private BFTNode nodeUnderTest;

  public OutdatedNodeForksTest() {
    bftTestBuilder =
        SimulationTest.builder()
            .numNodes(numValidators, Collections.nCopies(numValidators, UInt256.ONE))
            .networkModules(NetworkOrdering.inOrder(), NetworkLatencies.fixed())
            .fullFunctionNodes(SyncConfig.of(400L, 10, 2000L))
            .addRadixEngineConfigModules(new MockedForksModule(2L), new ForksModule())
            .addOverrideModuleToInitialNodes(
                nodes -> {
                  final var node = nodes.get(0).getPublicKey();
                  // a little hack to set the node under test key
                  nodeUnderTest = BFTNode.create(node);
                  return ImmutableList.of(node);
                },
                new RadixEngineForksGenesisOnlyModule() // the node under test starts with just the
                // genesis fork
                )
            .addNodeModule(MempoolConfig.asModule(1000, 10))
            .addTestModules(
                ConsensusMonitors.safety(),
                ConsensusMonitors.liveness(1, TimeUnit.SECONDS),
                ConsensusMonitors.directParents(),
                LedgerMonitors.consensusToLedger(),
                LedgerMonitors.ordered(),
                RadixEngineMonitors.noInvalidProposedCommands());
  }

  @Test
  public void outdated_node_should_recover_when_restarted_with_missing_forks() {
    final var simulationTest = bftTestBuilder.build();
    final var runningTest = simulationTest.run(Duration.ofSeconds(30));
    final var network = runningTest.getNetwork();
    final var correctNodes =
        network.getNodes().stream()
            .filter(not(node -> node.equals(nodeUnderTest)))
            .collect(ImmutableList.toImmutableList());

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
                  if (epochChange.getEpoch() == 11L) {
                    // verify that the correct nodes have executed fork1 and fork2 at correct epochs
                    final var maybeInvalidNode =
                        correctNodes.stream()
                            .filter(
                                node -> {
                                  final var storedForks =
                                      network.getInstance(ForksEpochStore.class, node);
                                  return storedForks.getStoredForks().containsKey(5)
                                      && storedForks.getStoredForks().containsKey(10);
                                })
                            .findAny();

                    maybeInvalidNode.ifPresent(
                        invalidNode ->
                            reportError.accept(
                                String.format(
                                    "Node %s should have executed forks at epoch 5 and 10 but it"
                                        + " hasn't",
                                    maybeInvalidNode.get())));

                    final var nodeUnderTestForks =
                        network.getInstance(ForksEpochStore.class, nodeUnderTest).getStoredForks();

                    if (nodeUnderTestForks.containsKey(5L) || nodeUnderTestForks.containsKey(10L)) {
                      reportError.accept(
                          "Expected test node to miss forks at epoch 5 and 10, but they've been"
                              + " executed");
                    }
                  } else if (epochChange.getEpoch() == 12L) {
                    // restart the test node at epoch 12 with the correct fork configuration
                    final var keyPair =
                        runningTest.getNetwork().getInstance(ECKeyPair.class, nodeUnderTest);
                    runningTest
                        .getNetwork()
                        .addOrOverrideNode(
                            keyPair,
                            new AbstractModule() {
                              @Override
                              protected void configure() {
                                // just override the fork config modifier module with a no-op
                                // (identity)
                                OptionalBinder.newOptionalBinder(
                                        binder(),
                                        new TypeLiteral<UnaryOperator<Set<ForkBuilder>>>() {})
                                    .setBinding()
                                    .toInstance(m -> m);
                              }
                            });
                  } else if (epochChange.getEpoch() == 13L) {
                    final var nodeUnderTestForks =
                        network.getInstance(ForksEpochStore.class, nodeUnderTest).getStoredForks();

                    if (!nodeUnderTestForks.containsKey(5L)
                        || !nodeUnderTestForks.containsKey(10L)) {
                      reportError.accept(
                          "Expected test node to execute missed forks at epoch 5 and 10");
                    }
                  }
                });

    final var results = runningTest.awaitCompletion();
    assertThat(results)
        .allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());

    if (firstError.get() != null) {
      Assert.fail(firstError.get());
    }
    testErrorsDisposable.dispose();

    // just a sanity check to make sure that at least 14 epochs have passed
    assertTrue(latestEpochChange.get().getEpoch() > 14);
  }
}
