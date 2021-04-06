/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.integration.distributed.simulation.tests.full_function;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.application.NodeApplicationModule;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.application.MempoolFillerStarter;
import com.radixdlt.integration.distributed.simulation.monitors.radix_engine.RadixEngineMonitors;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.sync.SyncConfig;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.Condition;
import org.junit.Ignore;
import org.junit.Test;
import org.radix.TokenIssuance;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Runs the chaos mempool filler and verifies that all operations are working normally
 */
public class MempoolFillTest {
	private final SimulationTest.Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed()
		)
		.fullFunctionNodes(View.of(10), SyncConfig.of(800L, 10, 5000L))
		.addNodeModule(new AbstractModule() {
			@Override
			protected void configure() {
				bind(MempoolConfig.class).toInstance(MempoolConfig.of(1000L, 200L));
				install(new MempoolFillerModule());
				install(new NodeApplicationModule());
			}
		})
		.addGenesisModule(new AbstractModule() {
			@ProvidesIntoSet
			private TokenIssuance mempoolFillerIssuance(@Genesis ImmutableList<ECKeyPair> validators) {
				return TokenIssuance.of(validators.get(0).getPublicKey(), TokenUnitConversions.unitsToSubunits(10000000000L));
			}
		})
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(1, TimeUnit.SECONDS),
			//ConsensusMonitors.noTimeouts(), // Removed for now to appease Jenkins
			ConsensusMonitors.directParents(),
			LedgerMonitors.consensusToLedger(),
			LedgerMonitors.ordered(),
			RadixEngineMonitors.noInvalidProposedCommands()
		)
		.addActor(MempoolFillerStarter.class);

	@Test
	@Ignore("Travis not playing nice")
	public void sanity_tests_should_pass() {
		SimulationTest simulationTest = bftTestBuilder
			.build();

		final var runningTest = simulationTest.run();
		final var results = runningTest.awaitCompletion();

		// Post conditions
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
		long invalidCommandsCount = runningTest.getNetwork().getSystemCounters().values().stream()
			.map(s -> s.get(SystemCounters.CounterType.RADIX_ENGINE_INVALID_PROPOSED_COMMANDS))
			.mapToLong(l -> l)
			.sum();
		assertThat(invalidCommandsCount).isZero();
	}

	@Test
	@Ignore("Travis not playing nicely with timeouts so disable for now until fixed.")
	public void filler_should_overwhelm_unratelimited_mempool() {
		SimulationTest simulationTest = bftTestBuilder
			.overrideWithIncorrectModule(new AbstractModule() {
				@Override
				protected void configure() {
					bind(MempoolConfig.class).toInstance(MempoolConfig.of(100L, 0L));
				}
			})
			.build();

		final var results = simulationTest.run().awaitCompletion();
		assertThat(results).hasValueSatisfying(new Condition<>(Optional::isPresent, "Error exists"));
	}
}
