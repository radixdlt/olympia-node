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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.environment.Runners;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.epochs.EpochsLocalSyncService;
import com.radixdlt.epochs.LocalSyncServiceFactory;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.sync.RemoteSyncService;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.sync.LocalSyncService;
import com.radixdlt.sync.LocalSyncService.InvalidSyncResponseSender;
import com.radixdlt.sync.LocalSyncService.VerifiedSyncResponseSender;
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
import com.radixdlt.sync.validation.RemoteSyncResponseSignaturesVerifier;

import java.util.Comparator;

/**
 * Epoch+Sync extension
 */
public class EpochsSyncModule extends AbstractModule {

	@Override
	public void configure() {
		bind(EpochsLocalSyncService.class).in(Scopes.SINGLETON);

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
	private EventProcessorOnRunner<?> epochsLedgerUpdateEventProcessorLocalSync(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return new EventProcessorOnRunner<>(
			Runners.SYNC,
			EpochsLedgerUpdate.class,
			epochsLocalSyncService.epochsLedgerUpdateEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> ledgerUpdateEventProcessorRemoteSync(
		RemoteSyncService remoteSyncService
	) {
		return new EventProcessorOnRunner<>(
			Runners.SYNC,
			EpochsLedgerUpdate.class,
			update -> remoteSyncService.ledgerUpdateEventProcessor().process(update.getBase())
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncCheckTriggerEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return new EventProcessorOnRunner<>(
			Runners.SYNC,
			SyncCheckTrigger.class,
			epochsLocalSyncService.syncCheckTriggerEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncCheckReceiveStatusTimeoutEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return new EventProcessorOnRunner<>(
			Runners.SYNC,
			SyncCheckReceiveStatusTimeout.class,
			epochsLocalSyncService.syncCheckReceiveStatusTimeoutEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncRequestTimeoutEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return new EventProcessorOnRunner<>(
			Runners.SYNC,
			SyncRequestTimeout.class,
			epochsLocalSyncService.syncRequestTimeoutEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncLedgerUpdateTimeoutProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return new EventProcessorOnRunner<>(
			Runners.SYNC,
			SyncLedgerUpdateTimeout.class,
			epochsLocalSyncService.syncLedgerUpdateTimeoutProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> localSyncRequestEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return new EventProcessorOnRunner<>(
			Runners.SYNC,
			LocalSyncRequest.class,
			epochsLocalSyncService.localSyncRequestEventProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> statusResponseEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.SYNC,
			StatusResponse.class,
			epochsLocalSyncService.statusResponseEventProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> syncResponseEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.SYNC,
			SyncResponse.class,
			epochsLocalSyncService.syncResponseEventProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> ledgerStatusUpdateEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.SYNC,
			LedgerStatusUpdate.class,
			epochsLocalSyncService.ledgerStatusUpdateEventProcessor()
		);
	}

	@Provides
	private LocalSyncServiceFactory localSyncServiceFactory(
		RemoteEventDispatcher<StatusRequest> statusRequestDispatcher,
		ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutDispatcher,
		RemoteEventDispatcher<SyncRequest> syncRequestDispatcher,
		ScheduledEventDispatcher<SyncRequestTimeout> syncRequestTimeoutDispatcher,
		ScheduledEventDispatcher<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutDispatcher,
		SyncConfig syncConfig,
		SystemCounters systemCounters,
		PeersView peersView,
		Comparator<AccumulatorState> accComparator,
		RemoteSyncResponseSignaturesVerifier signaturesVerifier,
		LedgerAccumulatorVerifier accumulatorVerifier,
		VerifiedSyncResponseSender verifiedSender,
		InvalidSyncResponseSender invalidSyncedCommandsSender
	) {
		return (remoteSyncResponseValidatorSetVerifier, syncState) ->
			new LocalSyncService(
				statusRequestDispatcher,
				syncCheckReceiveStatusTimeoutDispatcher,
				syncRequestDispatcher,
				syncRequestTimeoutDispatcher,
				syncLedgerUpdateTimeoutDispatcher,
				syncConfig,
				systemCounters,
				peersView,
				accComparator,
				remoteSyncResponseValidatorSetVerifier,
				signaturesVerifier,
				accumulatorVerifier,
				verifiedSender,
				invalidSyncedCommandsSender,
				syncState
			);
	}
}
