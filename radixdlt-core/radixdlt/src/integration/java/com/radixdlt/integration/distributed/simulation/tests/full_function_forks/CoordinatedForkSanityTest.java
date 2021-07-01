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

import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.radix_engine.RadixEngineMonitors;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.ForkManager;
import com.radixdlt.statecomputer.forks.ForkManagerModule;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.utils.UInt256;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertTrue;

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
				new MockedForksModule(View.of(numValidators * 2)),
				new ForkManagerModule()
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

		final var forks = network.getInstance(ForkManager.class, nodes.get(0)).forkConfigs();

		final var firstError = new AtomicReference<String>();
		final Consumer<String> reportError = err -> {
			if (firstError.get() == null) {
				firstError.set(err);
			}
		};

		final var latestEpochChange = new AtomicReference<EpochChange>();
		final var testErrorsDisposable = network.latestEpochChanges()
			.subscribe(epochChange -> {
				latestEpochChange.set(epochChange);

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
				} else if (epochChange.getEpoch() == 19L) {
					// still no change at epoch 19 (min epoch is 20)
					if (!verifyCurrentFork(network, forks.get(2))) {
						reportError.accept("Expected to be at a different fork (2) at epoch 19");
					}
				} else if (epochChange.getEpoch() == 21L) {
					// verify that at epoch 21 we've successfully switched to the fork with voting (idx 3)
					if (!verifyCurrentFork(network, forks.get(3))) {
						reportError.accept("Expected to be at a different fork (3) at epoch 20");
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

		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

	private void updateValidatorWithLatestFork(RunningNetwork network, BFTNode node) {
		final var forkManager = network.getInstance(ForkManager.class, node);
		final var maybeForkVoteHash =
			forkManager.getCandidateFork().map(f -> ForkConfig.voteHash(node.getKey(), f));
		final var txRequest = TxnConstructionRequest.create()
			.updateValidatorMetadata(node.getKey(), node.getSimpleName(), "", maybeForkVoteHash);
		network.getDispatcher(NodeApplicationRequest.class, node)
			.dispatch(NodeApplicationRequest.create(txRequest));
	}

	private boolean verifyCurrentFork(RunningNetwork network, ForkConfig forkConfig) {
		return network.getNodes().stream().allMatch(node -> {
			final var forkManager = network.getInstance(ForkManager.class, node);
			final var epochsForks = network.getInstance(CommittedReader.class, node).getEpochsForkHashes();
			return forkManager.getCurrentFork(epochsForks).getHash().equals(forkConfig.getHash());
		});
	}
}
