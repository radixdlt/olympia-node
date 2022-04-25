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

package com.radixdlt.integration.steady_state.simulation.full_function;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.harness.simulation.NetworkLatencies;
import com.radixdlt.harness.simulation.NetworkOrdering;
import com.radixdlt.harness.simulation.SimulationTest;
import com.radixdlt.harness.simulation.application.MempoolFillerStarter;
import com.radixdlt.harness.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.harness.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.harness.simulation.monitors.radix_engine.RadixEngineMonitors;
import com.radixdlt.integration.targeted.mempool.MempoolFillerModule;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.sync.SyncConfig;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.Condition;
import org.junit.Ignore;
import org.junit.Test;
import com.radixdlt.statecomputer.checkpoint.TokenIssuance;

/** Runs the chaos mempool filler and verifies that all operations are working normally */
public class MempoolFillTest {
  private final SimulationTest.Builder bftTestBuilder =
      SimulationTest.builder()
          .numNodes(4)
          .networkModules(NetworkOrdering.inOrder(), NetworkLatencies.fixed())
          .fullFunctionNodes(SyncConfig.of(800L, 10, 5000L))
          .addRadixEngineConfigModules(
              new MainnetForksModule(), new RadixEngineForksLatestOnlyModule(), new ForksModule())
          .addNodeModule(
              new AbstractModule() {
                @Override
                protected void configure() {
                  install(MempoolConfig.asModule(1000, 200));
                  install(new MempoolFillerModule());
                }

                @ProvidesIntoSet
                private TokenIssuance mempoolFillerIssuance(
                    @Genesis ImmutableList<ECPublicKey> validators) {
                  return TokenIssuance.of(
                      validators.get(0), TokenUnitConversions.unitsToSubunits(10000000000L));
                }
              })
          .addTestModules(
              ConsensusMonitors.safety(),
              ConsensusMonitors.liveness(1, TimeUnit.SECONDS),
              // ConsensusMonitors.noTimeouts(), // Removed for now to appease Jenkins
              ConsensusMonitors.directParents(),
              LedgerMonitors.consensusToLedger(),
              LedgerMonitors.ordered(),
              RadixEngineMonitors.noInvalidProposedCommands())
          .addActor(MempoolFillerStarter.class);

  @Test
  @Ignore("Travis not playing nice")
  public void sanity_tests_should_pass() {
    SimulationTest simulationTest = bftTestBuilder.build();

    final var runningTest = simulationTest.run();
    final var results = runningTest.awaitCompletion();

    // Post conditions
    assertThat(results)
        .allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
    long invalidCommandsCount =
        runningTest.getNetwork().getSystemCounters().values().stream()
            .map(s -> s.get(SystemCounters.CounterType.RADIX_ENGINE_INVALID_PROPOSED_COMMANDS))
            .mapToLong(l -> l)
            .sum();
    assertThat(invalidCommandsCount).isZero();
  }

  @Test
  @Ignore("Travis not playing nicely with timeouts so disable for now until fixed.")
  public void filler_should_overwhelm_unratelimited_mempool() {
    SimulationTest simulationTest =
        bftTestBuilder.addOverrideModuleToAllInitialNodes(MempoolConfig.asModule(100, 0)).build();

    final var results = simulationTest.run().awaitCompletion();
    assertThat(results).hasValueSatisfying(new Condition<>(Optional::isPresent, "Error exists"));
  }
}
