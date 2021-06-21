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
 */

package com.radixdlt.integration.distributed.simulation.tests.full_function_forks;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Key;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.actions.UpdateValidator;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.radix_engine.RadixEngineMonitors;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.BetanetForksModule;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.ForkManager;
import com.radixdlt.statecomputer.forks.ForkOverwritesWithShorterEpochsModule;
import com.radixdlt.store.EngineStore;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.utils.UInt256;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertEquals;

public final class CoordinatedForkSanityTest {
	private final Builder bftTestBuilder;

	private final int numValidators = 4;

	public CoordinatedForkSanityTest() {
		bftTestBuilder = SimulationTest.builder()
			.numNodes(numValidators, numValidators, Collections.nCopies(numValidators, UInt256.ONE))
			.networkModules(
				NetworkOrdering.inOrder(),
				NetworkLatencies.fixed()
			)
			.fullFunctionNodes(SyncConfig.of(400L, 10, 2000L))
			.addRadixEngineConfigModules(
				new ForkOverwritesWithShorterEpochsModule(false, 2, ImmutableSet.of(BetanetForksModule.betanetV4().getName())),
				new BetanetForksModule()
			)
			.addNodeModule(MempoolConfig.asModule(1000, 10))
			.addTestModules(
				ConsensusMonitors.safety(),
				ConsensusMonitors.liveness(1, TimeUnit.SECONDS),
				ConsensusMonitors.noTimeouts(),
				ConsensusMonitors.directParents(),
				LedgerMonitors.consensusToLedger(),
				LedgerMonitors.ordered(),
				RadixEngineMonitors.noInvalidProposedCommands()
			);
	}

	@Test
	public void sanity_tests_should_pass() {
		final var simulationTest = bftTestBuilder.build();
		final var runningTest = simulationTest.run(Duration.ofSeconds(40));
		final var network = runningTest.getNetwork();
		final var nodes = network.getNodes();
		final var halfOfTheNodes = nodes.subList(0, nodes.size() / 2);
		final var oneMoreNode = nodes.get(nodes.size() / 2);

		final var latestEpoch = new AtomicLong();
		network.latestEpochChanges()
			.forEach(epochChange -> {
				latestEpoch.set(epochChange.getEpoch());
				if (epochChange.getEpoch() == 10L) {
					// half nodes vote in epoch 10
					halfOfTheNodes.forEach(node -> updateValidatorWithLatestFork(network, node));
				} else if (epochChange.getEpoch() == 11L) {
					// verify that at epoch 11 the network is still at fork V3
					verifyCurrentFork(network, BetanetForksModule.betanetV3());
				} else if (epochChange.getEpoch() == 12L) {
					// verify that at epoch 12 the network is still at fork V3
					verifyCurrentFork(network, BetanetForksModule.betanetV3());

					// one more node votes in epoch 12
					updateValidatorWithLatestFork(network, oneMoreNode);
				}
			});

		final var results = runningTest.awaitCompletion();

		// betenat4 has an unchanged high view so the test shouldn't advance any further
		assertEquals(13, latestEpoch.get());

		// verify that at the end of test all nodes have switched to V4
		verifyCurrentFork(network, BetanetForksModule.betanetV4());

		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

	private void updateValidatorWithLatestFork(RunningNetwork network, BFTNode node) {
		final var forkManager = network.getInstance(ForkManager.class, node);
		final var action = new UpdateValidator(
			node.getKey(), node.getSimpleName(), "",
			Optional.of(ForkConfig.hashOf(forkManager.latestKnownFork()))
		);
		network.getDispatcher(NodeApplicationRequest.class, node)
			.dispatch(NodeApplicationRequest.create(action));
	}

	private void verifyCurrentFork(RunningNetwork network, ForkConfig forkConfig) {
		network.getNodes().forEach(node -> {
			if (!network.getInstance(new Key<EngineStore<LedgerAndBFTProof>>() {}, node).getCurrentForkHash().get()
					.equals(ForkConfig.hashOf(forkConfig))) {
				throw new IllegalStateException("Expected the network to be at fork: " + forkConfig.getName());
			}
		});
	}
}
