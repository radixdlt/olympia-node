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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerKey;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.integration.distributed.simulation.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.Condition;
import org.junit.Test;
import org.radix.TokenIssuance;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Runs the chaos mempool filler and verifies that all operations are working normally
 */
public class MempoolFillTest {
	private final ECKeyPair mempoolFillerKey = ECKeyPair.generateNew();

	private final SimulationTest.Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed()
		)
		.fullFunctionNodes(View.of(10), 200)
		.addNodeModule(new AbstractModule() {
			@Override
			protected void configure() {
				bind(ECKeyPair.class).annotatedWith(MempoolFillerKey.class).toInstance(mempoolFillerKey);
				bindConstant().annotatedWith(MempoolThrottleMs.class).to(10L);
				bindConstant().annotatedWith(MempoolMaxSize.class).to(1000);
				install(new MempoolFillerModule());
			}

			@ProvidesIntoSet
			private TokenIssuance mempoolFillerIssuance(@MempoolFillerKey ECPublicKey mempoolFillerKey) {
				return TokenIssuance.of(mempoolFillerKey, TokenUnitConversions.unitsToSubunits(10000000000L));
			}
		})
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(1, TimeUnit.SECONDS),
			ConsensusMonitors.noTimeouts(),
			ConsensusMonitors.directParents(),
			LedgerMonitors.consensusToLedger(),
			LedgerMonitors.ordered()
		)
		.addActor(new SimulationTest.SimulationNetworkActor() {
			@Override
			public void start(SimulationNodes.RunningNetwork network) {
				EventDispatcher<MempoolFillerUpdate> dispatcher = network
					.getDispatcher(MempoolFillerUpdate.class, network.getNodes().get(0));
				dispatcher.dispatch(MempoolFillerUpdate.create(true));
			}

			@Override
			public void stop() {
			}
		});

	@Test
	public void sanity_tests_should_pass() {
		SimulationTest simulationTest = bftTestBuilder
			.build();

		SimulationTest.TestResults results = simulationTest.run();
		assertThat(results.getCheckResults()).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}

	@Test
	public void filler_should_overwhelm_unratelimited_mempool() {
		SimulationTest simulationTest = bftTestBuilder
			.overrideWithIncorrectModule(new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(MempoolThrottleMs.class).to(0L);
				}
			})
			.build();

		SimulationTest.TestResults results = simulationTest.run();
		assertThat(results.getCheckResults()).hasValueSatisfying(new Condition<>(Optional::isPresent, "Error exists"));
	}
}
