/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger_sync;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.radixdlt.SyncModuleRunner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessWithSyncRunner;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.environment.rx.ModuleRunnerImpl;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.DelayedSyncModuleRunner;
import com.radixdlt.integration.distributed.simulation.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import org.junit.Test;

import java.time.Duration;
import java.util.Set;
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
			.addSingleByzantineModule(NODE_UNDER_TEST_INDEX, new AbstractModule() {
				@Override
				protected void configure() {
					bind(new TypeLiteral<SyncModuleRunner>() { })
						.to(new TypeLiteral<DelayedSyncModuleRunner>() { });
				}

				@Provides
				private DelayedSyncModuleRunner delayedSyncRunner(
					@Self BFTNode self,
					ScheduledEventDispatcher<SyncCheckTrigger> syncCheckTriggerDispatcher,
					SyncConfig syncConfig,
					Observable<LocalSyncRequest> localSyncRequests,
					EventProcessor<LocalSyncRequest> syncRequestEventProcessor,
					Observable<SyncCheckTrigger> syncCheckTriggers,
					EventProcessor<SyncCheckTrigger> syncCheckTriggerProcessor,
					Observable<SyncRequestTimeout> syncRequestTimeouts,
					EventProcessor<SyncRequestTimeout> syncRequestTimeoutProcessor,
					Observable<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeouts,
					EventProcessor<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutProcessor,
					Observable<EpochsLedgerUpdate> ledgerUpdates,
					@ProcessWithSyncRunner Set<EventProcessor<EpochsLedgerUpdate>> ledgerUpdateProcessors,
					Flowable<RemoteEvent<StatusRequest>> remoteStatusRequests,
					RemoteEventProcessor<StatusRequest> statusRequestProcessor,
					Flowable<RemoteEvent<StatusResponse>> remoteStatusResponses,
					RemoteEventProcessor<StatusResponse> statusResponseProcessor,
					Flowable<RemoteEvent<SyncRequest>> remoteSyncRequests,
					RemoteEventProcessor<SyncRequest> remoteSyncServiceProcessor,
					Flowable<RemoteEvent<SyncResponse>> remoteSyncResponses,
					RemoteEventProcessor<SyncResponse> responseProcessor
				) {
					final var baseRunner = (ModuleRunnerImpl.builder()
						.add(localSyncRequests, syncRequestEventProcessor)
						.add(syncCheckTriggers, syncCheckTriggerProcessor)
						.add(syncCheckReceiveStatusTimeouts, syncCheckReceiveStatusTimeoutProcessor)
						.add(syncRequestTimeouts, syncRequestTimeoutProcessor)
						.add(ledgerUpdates, e -> ledgerUpdateProcessors.forEach(p -> p.process(e)))
						.add(remoteStatusRequests, statusRequestProcessor)
						.add(remoteStatusResponses, statusResponseProcessor)
						.add(remoteSyncRequests, remoteSyncServiceProcessor)
						.add(remoteSyncResponses, responseProcessor)
						.onStart(() -> syncCheckTriggerDispatcher.dispatch(
								SyncCheckTrigger.create(),
								syncConfig.syncCheckInterval()
						))
						.build("SyncManager " + self));

					return new DelayedSyncModuleRunner(baseRunner, SYNC_DELAY);
				}
			})
			.overrideWithIncorrectModule(new AbstractModule() {
				@Provides
				public BFTValidatorSet genesisValidatorSet(Function<Long, BFTValidatorSet> mapper) {
					return mapper.apply(0L);
				}
			})
			.pacemakerTimeout(1000)
			.ledgerAndEpochsAndSync(View.of(10), (unused) -> IntStream.of(0, 1), SyncConfig.of(50L, 10, 50L))
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
		final var results = simulationTest.run(Duration.ofSeconds(15));
		final var nodeCounters = results.getNetwork()
			.getSystemCounters().get(results.getNetwork().getNodes().get(NODE_UNDER_TEST_INDEX));

		assertThat(results.getCheckResults()).allSatisfy((name, err) -> assertThat(err).isEmpty());

		// node must be synced up to some state after the first epoch
		// and must not fall behind too much
		assertTrue(nodeCounters.get(CounterType.SYNC_TARGET_CURRENT_DIFF) < 200);
		assertTrue(nodeCounters.get(CounterType.SYNC_PROCESSED) > 200);
		assertTrue(nodeCounters.get(CounterType.LEDGER_PROCESSED) > 200);
		assertTrue(nodeCounters.get(CounterType.LEDGER_STATE_VERSION) > 200);
		// just to be sure that node wasn't a validator
		assertEquals(0, nodeCounters.get(CounterType.BFT_PROPOSALS_MADE));
		assertEquals(0, nodeCounters.get(CounterType.BFT_PROCESSED));
	}
}
