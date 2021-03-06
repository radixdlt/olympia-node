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
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.ProcessWithSyncRunner;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.epochs.EpochsLocalSyncService;
import com.radixdlt.epochs.LocalSyncServiceFactory;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.network.addressbook.PeersView;
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
	@ProcessWithSyncRunner
	private EventProcessor<EpochsLedgerUpdate> epochsLedgerUpdateEventProcessorLocalSync(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return epochsLocalSyncService.epochsLedgerUpdateEventProcessor();
	}

	@ProvidesIntoSet
	@ProcessWithSyncRunner
	private EventProcessor<EpochsLedgerUpdate> ledgerUpdateEventProcessorRemoteSync(
		RemoteSyncService remoteSyncService
	) {
		return update -> remoteSyncService.ledgerUpdateEventProcessor().process(update.getBase());
	}

	@Provides
	private EventProcessor<SyncCheckTrigger> syncCheckTriggerEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return epochsLocalSyncService.syncCheckTriggerEventProcessor();
	}

	@Provides
	private EventProcessor<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return epochsLocalSyncService.syncCheckReceiveStatusTimeoutEventProcessor();
	}

	@Provides
	private EventProcessor<SyncRequestTimeout> syncRequestTimeoutEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return epochsLocalSyncService.syncRequestTimeoutEventProcessor();
	}

	@Provides
	private EventProcessor<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return epochsLocalSyncService.syncLedgerUpdateTimeoutProcessor();
	}

	@Provides
	private EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return epochsLocalSyncService.localSyncRequestEventProcessor();
	}

	@Provides
	private RemoteEventProcessor<StatusResponse> statusResponseEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return epochsLocalSyncService.statusResponseEventProcessor();
	}

	@Provides
	private RemoteEventProcessor<SyncResponse> syncResponseEventProcessor(
		EpochsLocalSyncService epochsLocalSyncService
	) {
		return epochsLocalSyncService.syncResponseEventProcessor();
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
		Hasher hasher,
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
				hasher,
				remoteSyncResponseValidatorSetVerifier,
				signaturesVerifier,
				accumulatorVerifier,
				verifiedSender,
				invalidSyncedCommandsSender,
				syncState
			);
	}
}
