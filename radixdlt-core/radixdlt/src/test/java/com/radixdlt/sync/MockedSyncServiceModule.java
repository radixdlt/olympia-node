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

package com.radixdlt.sync;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.Runners;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncLedgerUpdateTimeout;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.LongStream;

public class MockedSyncServiceModule extends AbstractModule {
	private static final Logger logger = LogManager.getLogger();

	private final ConcurrentMap<Long, Txn> sharedCommittedCommands;
	private final ConcurrentMap<Long, LedgerProof> sharedEpochProofs;

	public MockedSyncServiceModule() {
		this.sharedCommittedCommands = new ConcurrentHashMap<>();
		this.sharedEpochProofs = new ConcurrentHashMap<>();
	}

	@Override
	public void configure() {
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(SyncCheckTrigger.class);
		eventBinder.addBinding().toInstance(SyncCheckReceiveStatusTimeout.class);
		eventBinder.addBinding().toInstance(SyncRequestTimeout.class);
		eventBinder.addBinding().toInstance(LocalSyncRequest.class);
		eventBinder.addBinding().toInstance(SyncLedgerUpdateTimeout.class);
		eventBinder.addBinding().toInstance(EpochsLedgerUpdate.class);
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor() {
		return update -> {
			final LedgerProof headerAndProof = update.getTail();
			long stateVersion = headerAndProof.getAccumulatorState().getStateVersion();
			long firstVersion = stateVersion - update.getNewTxns().size() + 1;
			for (int i = 0; i < update.getNewTxns().size(); i++) {
				sharedCommittedCommands.put(firstVersion + i, update.getNewTxns().get(i));
			}

			if (update.getTail().isEndOfEpoch()) {
				logger.info("Epoch Proof: " + (update.getTail().getEpoch() + 1));
				sharedEpochProofs.put(update.getTail().getEpoch() + 1, update.getTail());
			}
		};
	}

	@ProvidesIntoSet
	@Singleton
	@ProcessOnDispatch
	EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor(
		@LastEpochProof LedgerProof genesis,
		EventDispatcher<VerifiedTxnsAndProof> syncCommandsDispatcher
	) {
		return new EventProcessor<>() {
			long currentVersion = genesis.getStateVersion();
			long currentEpoch = genesis.getEpoch() + 1;

			private void syncTo(LedgerProof proof) {
				var txns = LongStream.range(currentVersion + 1, proof.getStateVersion() + 1)
					.peek(v -> logger.info("{} {}", v, sharedCommittedCommands.get(v)))
					.mapToObj(sharedCommittedCommands::get)
					.collect(ImmutableList.toImmutableList());
				syncCommandsDispatcher.dispatch(VerifiedTxnsAndProof.create(txns, proof));
				currentVersion = proof.getStateVersion();
				if (proof.isEndOfEpoch()) {
					currentEpoch = proof.getEpoch() + 1;
				} else {
					currentEpoch = proof.getEpoch();
				}
			}

			@Override
			public void process(LocalSyncRequest request) {
				while (currentEpoch < request.getTarget().getEpoch()) {
					if (!sharedEpochProofs.containsKey(currentEpoch + 1)) {
						throw new IllegalStateException("Epoch proof does not exist: " + currentEpoch + 1);
					}

					syncTo(sharedEpochProofs.get(currentEpoch + 1));
				}

				syncTo(request.getTarget());

				final long targetVersion = request.getTarget().getStateVersion();
				var txns = LongStream.range(currentVersion + 1, targetVersion + 1)
					.mapToObj(sharedCommittedCommands::get)
					.collect(ImmutableList.toImmutableList());

				syncCommandsDispatcher.dispatch(VerifiedTxnsAndProof.create(txns, request.getTarget()));
				currentVersion = targetVersion;
				currentEpoch = request.getTarget().getEpoch();
			}
		};
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> ledgerStatusUpdateRemoteEventProcessor(
		EventDispatcher<LocalSyncRequest> localSyncRequestEventDispatcher
	) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.SYNC,
			LedgerStatusUpdate.class,
			(sender, ev) -> localSyncRequestEventDispatcher.dispatch(new LocalSyncRequest(ev.getHeader(), ImmutableList.of(sender)))
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> epochsLedgerUpdateEventProcessor() {
		return noOpProcessor(EpochsLedgerUpdate.class);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncCheckTriggerEventProcessor() {
		return noOpProcessor(SyncCheckTrigger.class);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncCheckReceiveStatusTimeoutEventProcessor() {
		return noOpProcessor(SyncCheckReceiveStatusTimeout.class);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncRequestTimeoutEventProcessor() {
		return noOpProcessor(SyncRequestTimeout.class);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> localSyncRequestEventProcessor() {
		return noOpProcessor(LocalSyncRequest.class);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncLedgerUpdateTimeoutEventProcessor() {
		return noOpProcessor(SyncLedgerUpdateTimeout.class);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> statusRequestEventProcessor() {
		return noOpRemoteProcessor(StatusRequest.class);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> statusResponseEventProcessor() {
		return noOpRemoteProcessor(StatusResponse.class);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> syncRequestEventProcessor() {
		return noOpRemoteProcessor(SyncRequest.class);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> syncResponseEventProcessor() {
		return noOpRemoteProcessor(SyncResponse.class);
	}

	private EventProcessorOnRunner<?> noOpProcessor(Class<?> clazz) {
		return new EventProcessorOnRunner<>(Runners.SYNC, clazz, ev -> { });
	}

	private RemoteEventProcessorOnRunner<?> noOpRemoteProcessor(Class<?> clazz) {
		return new RemoteEventProcessorOnRunner<>(Runners.SYNC, clazz, (sender, ev) -> { });
	}
}
