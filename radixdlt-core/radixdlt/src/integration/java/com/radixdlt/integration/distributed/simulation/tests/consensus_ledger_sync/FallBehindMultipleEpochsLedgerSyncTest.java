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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger_sync;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.Runners;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.monitors.ledger.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.sync.SyncConfig;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A node that falls behind multiple epochs should be able to successfully sync its ledger
 * and keep up once synced.
 */
public class FallBehindMultipleEpochsLedgerSyncTest {
	private static final int NODE_UNDER_TEST_INDEX = 2;
	private static final long SYNC_DELAY = 5000L;

	private final Builder testBuilder;

	public FallBehindMultipleEpochsLedgerSyncTest() {
		this.testBuilder = SimulationTest.builder()
			.numNodes(3)
			.networkModules(
				NetworkOrdering.inOrder(),
				NetworkLatencies.fixed(10)
			)
			.overrideWithIncorrectModule(new AbstractModule() {
				@Provides
				public BFTValidatorSet genesisValidatorSet(Function<Long, BFTValidatorSet> mapper) {
					return mapper.apply(0L);
				}
			})
			.pacemakerTimeout(3000)
			.ledgerAndEpochsAndSync(View.of(10), (unused) -> IntStream.of(0, 1), SyncConfig.of(200L, 10, 2000L))
			.addTestModules(
				ConsensusMonitors.safety(),
				ConsensusMonitors.liveness(5, TimeUnit.SECONDS),
				ConsensusMonitors.directParents(),
				LedgerMonitors.consensusToLedger(),
				LedgerMonitors.ordered(),
				ConsensusMonitors.epochCeilingView(View.of(10))
			);
	}

	@Test
	public void given_a_node_that_falls_behind_multiple_epochs__it_should_sync_up() {
		final var simulationTest = testBuilder.build();

		final var runningTest = simulationTest.run(
			Duration.ofSeconds(15),
			ImmutableMap.of(NODE_UNDER_TEST_INDEX, ImmutableSet.of(Runners.SYNC))
		);

		Executors.newSingleThreadScheduledExecutor()
			.schedule(() -> runningTest.getNetwork().runModule(NODE_UNDER_TEST_INDEX, Runners.SYNC), SYNC_DELAY, TimeUnit.MILLISECONDS);

		final var results = runningTest.awaitCompletion();

		final var nodeCounters = runningTest.getNetwork()
			.getSystemCounters().get(runningTest.getNetwork().getNodes().get(NODE_UNDER_TEST_INDEX));

		assertThat(results).allSatisfy((name, err) -> assertThat(err).isEmpty());

		// node must be synced up to some state after the first epoch
		// and must not fall behind too much
		assertTrue(nodeCounters.get(CounterType.SYNC_TARGET_CURRENT_DIFF) < 200);
		assertTrue(nodeCounters.get(CounterType.SYNC_PROCESSED) > 200);
		assertTrue(nodeCounters.get(CounterType.LEDGER_STATE_VERSION) > 200);
		// just to be sure that node wasn't a validator
		assertEquals(0, nodeCounters.get(CounterType.BFT_PROPOSALS_MADE));
		assertEquals(0, nodeCounters.get(CounterType.BFT_PROCESSED));
	}
}
