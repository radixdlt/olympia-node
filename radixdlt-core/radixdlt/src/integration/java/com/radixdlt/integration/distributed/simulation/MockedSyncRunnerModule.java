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

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.ProcessWithSyncRunner;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.rx.ModuleRunnerImpl;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncService;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncLedgerUpdateTimeout;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import com.radixdlt.utils.ThreadFactories;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A sync runner which doesn't involve epochs
 */
public class MockedSyncRunnerModule extends AbstractModule {

	@Override
	public void configure() {
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(SyncCheckTrigger.class);
		eventBinder.addBinding().toInstance(SyncCheckReceiveStatusTimeout.class);
		eventBinder.addBinding().toInstance(SyncRequestTimeout.class);
		eventBinder.addBinding().toInstance(LocalSyncRequest.class);
		eventBinder.addBinding().toInstance(SyncLedgerUpdateTimeout.class);
	}

	@ProvidesIntoMap
	@StringMapKey("sync")
	private ModuleRunner syncRunner(
		@Self BFTNode self,
		EventDispatcher<SyncCheckTrigger> syncCheckTriggerDispatcher,
		SyncConfig syncConfig,
		Observable<LocalSyncRequest> localSyncRequests,
		EventProcessor<LocalSyncRequest> syncRequestEventProcessor,
		Observable<SyncCheckTrigger> syncCheckTriggers,
		EventProcessor<SyncCheckTrigger> syncCheckTriggerProcessor,
		Observable<SyncRequestTimeout> syncRequestTimeouts,
		EventProcessor<SyncRequestTimeout> syncRequestTimeoutProcessor,
		Observable<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeouts,
		EventProcessor<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutProcessor,
		Observable<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeouts,
		EventProcessor<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutProcessor,
		Observable<LedgerUpdate> ledgerUpdates,
		@ProcessWithSyncRunner Set<EventProcessor<LedgerUpdate>> ledgerUpdateProcessors,
		Flowable<RemoteEvent<StatusRequest>> remoteStatusRequests,
		RemoteEventProcessor<StatusRequest> statusRequestProcessor,
		Flowable<RemoteEvent<StatusResponse>> remoteStatusResponses,
		RemoteEventProcessor<StatusResponse> statusResponseProcessor,
		Flowable<RemoteEvent<SyncRequest>> remoteSyncRequests,
		RemoteEventProcessor<SyncRequest> remoteSyncRequestProcessor,
		Flowable<RemoteEvent<SyncResponse>> remoteSyncResponses,
		RemoteEventProcessor<SyncResponse> syncResponseProcessor
	) {
		final var executor =
			Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("Sync " + self));

		return ModuleRunnerImpl.builder()
			.add(localSyncRequests, syncRequestEventProcessor)
			.add(syncCheckTriggers, syncCheckTriggerProcessor)
			.add(syncCheckReceiveStatusTimeouts, syncCheckReceiveStatusTimeoutProcessor)
			.add(syncRequestTimeouts, syncRequestTimeoutProcessor)
			.add(syncLedgerUpdateTimeouts, syncLedgerUpdateTimeoutProcessor)
			.add(ledgerUpdates, e -> ledgerUpdateProcessors.forEach(p -> p.process(e)))
			.add(remoteStatusRequests, statusRequestProcessor)
			.add(remoteStatusResponses, statusResponseProcessor)
			.add(remoteSyncRequests, remoteSyncRequestProcessor)
			.add(remoteSyncResponses, syncResponseProcessor)
				.onStart(() -> {
					executor.scheduleWithFixedDelay(
						() -> syncCheckTriggerDispatcher.dispatch(SyncCheckTrigger.create()),
						syncConfig.syncCheckInterval(),
						syncConfig.syncCheckInterval(),
						TimeUnit.MILLISECONDS
					);
				})
				.onStop(() -> {
					try {
						executor.awaitTermination(10L, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				})
			.build("SyncManager " + self);
	}

	@ProvidesIntoSet
	@ProcessWithSyncRunner
	private EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor(
		LocalSyncService localSyncService
	) {
		return localSyncService.ledgerUpdateEventProcessor();
	}

	@Provides
	public EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor(
		LocalSyncService localSyncService
	) {
		return localSyncService.localSyncRequestEventProcessor();
	}

	@Provides
	public EventProcessor<SyncCheckTrigger> syncCheckTriggerEventProcessor(
		LocalSyncService localSyncService
	) {
		return localSyncService.syncCheckTriggerEventProcessor();
	}

	@Provides
	private EventProcessor<SyncRequestTimeout> syncRequestTimeoutEventProcessor(
		LocalSyncService localSyncService
	) {
		return localSyncService.syncRequestTimeoutEventProcessor();
	}

	@Provides
	private EventProcessor<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutEventProcessor(
		LocalSyncService localSyncService
	) {
		return localSyncService.syncLedgerUpdateTimeoutProcessor();
	}

	@Provides
	private EventProcessor<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutEventProcessor(
		LocalSyncService localSyncService
	) {
		return localSyncService.syncCheckReceiveStatusTimeoutEventProcessor();
	}

	@Provides
	private RemoteEventProcessor<StatusResponse> statusResponseEventProcessor(
		LocalSyncService localSyncService
	) {
		return localSyncService.statusResponseEventProcessor();
	}

	@Provides
	private RemoteEventProcessor<SyncResponse> syncResponseEventProcessor(
		LocalSyncService localSyncService
	) {
		return localSyncService.syncResponseEventProcessor();
	}
}
