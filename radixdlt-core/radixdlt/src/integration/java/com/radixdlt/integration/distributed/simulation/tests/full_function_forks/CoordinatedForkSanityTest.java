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

import com.google.inject.Key;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.actions.UpdateValidator;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
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
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.ForkManager;
import com.radixdlt.statecomputer.forks.RadixEngineForksModule;
import com.radixdlt.store.EngineStore;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.utils.UInt256;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

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
				new MockedForksModule(View.of(numValidators * 2 + 1)),
				new RadixEngineForksModule()
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
		final var runningTest = simulationTest.run(Duration.ofSeconds(30));
		final var network = runningTest.getNetwork();
		final var nodes = network.getNodes();
		final var halfOfTheNodes = nodes.subList(0, nodes.size() / 2);
		final var oneMoreNode = nodes.get(nodes.size() / 2);

		final var forks = network.getInstance(ForkManager.class, nodes.get(0)).forksConfigs();

		final var firstError = new AtomicReference<String>();
		final Consumer<String> reportError = err -> {
			if (firstError.get() == null) {
				firstError.set(err);
			}
		};
		final var testErrorsDisposable = network.latestEpochChanges()
			.subscribe(epochChange -> {
				// just a sanity check that all validators have the same power (this test depends on this assumption)
				final var validatorSet = epochChange.getBFTConfiguration().getValidatorSet();
				final var validatorPower = validatorSet.getValidators().iterator().next().getPower();
				final var allValidatorsHaveTheSamePower = validatorSet.getValidators().stream()
					.allMatch(v -> v.getPower().equals(validatorPower));

				if (!allValidatorsHaveTheSamePower) {
					reportError.accept("Expected all validators to have the same power");
				}

				if (epochChange.getEpoch() == 10L) {
					// half nodes vote in epoch 10
					halfOfTheNodes.forEach(node -> updateValidatorWithLatestFork(network, node));
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
					updateValidatorWithLatestFork(network, oneMoreNode);
				} else if (epochChange.getEpoch() == 14L) {
					// verify that at epoch 14 we've successfully switched to the fork with voting (idx 3)
					if (!verifyCurrentFork(network, forks.get(3))) {
						reportError.accept("Expected to be at a different fork (3) at epoch 13");
					}
				}
			});

		final var results = runningTest.awaitCompletion();

		if (firstError.get() != null) {
			Assert.fail(firstError.get());
		}
		testErrorsDisposable.dispose();

		// verify that at the end of test all nodes are at fork idx 3
		if (!verifyCurrentFork(network, forks.get(3))) {
			throw new IllegalStateException("Expected to be at a different fork (3) at the end of test");
		}

		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

	private void updateValidatorWithLatestFork(RunningNetwork network, BFTNode node) {
		final var forkManager = network.getInstance(ForkManager.class, node);
		final var action = new UpdateValidator(
			node.getKey(), node.getSimpleName(), "",
			Optional.of(ForkConfig.voteHash(node.getKey(), forkManager.latestKnownFork()))
		);
		network.getDispatcher(NodeApplicationRequest.class, node)
			.dispatch(NodeApplicationRequest.create(action));
	}

	private boolean verifyCurrentFork(RunningNetwork network, ForkConfig forkConfig) {
		return network.getNodes().stream().allMatch(node ->
			network.getInstance(new Key<EngineStore<LedgerAndBFTProof>>() {}, node).getCurrentForkHash().get()
				.equals(forkConfig.getHash())
		);
	}
}
