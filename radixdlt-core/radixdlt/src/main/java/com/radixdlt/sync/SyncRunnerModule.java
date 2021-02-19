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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessWithSyncRunner;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.rx.ModuleRunnerImpl;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.epochs.EpochsLedgerUpdate;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

public class SyncRunnerModule extends AbstractModule {

	private static final Logger log = LogManager.getLogger();

	@ProvidesIntoMap
	@StringMapKey("sync")
	private ModuleRunner syncRunner(
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
		RemoteEventProcessor<SyncRequest> remoteSyncRequestProcessor,
		Flowable<RemoteEvent<SyncResponse>> remoteSyncResponses,
		RemoteEventProcessor<SyncResponse> syncResponseProcessor
	) {
		return ModuleRunnerImpl.builder()
			.add(localSyncRequests, syncRequestEventProcessor)
			.add(syncCheckTriggers, syncCheckTriggerProcessor)
			.add(syncCheckReceiveStatusTimeouts, syncCheckReceiveStatusTimeoutProcessor)
			.add(syncRequestTimeouts, syncRequestTimeoutProcessor)
			.add(ledgerUpdates, e -> ledgerUpdateProcessors.forEach(p -> p.process(e)))
			.add(remoteStatusRequests, statusRequestProcessor)
			.add(remoteStatusResponses, statusResponseProcessor)
			.add(remoteSyncRequests, remoteSyncRequestProcessor)
			.add(remoteSyncResponses, syncResponseProcessor)
			.onStart(() -> {
				final var sc = SyncCheckTrigger.create();
				log.info("Calling onStart sync runner module, scheduling sending sync check trigger {}", sc);
				syncCheckTriggerDispatcher.dispatch(
						sc,
						syncConfig.syncCheckInterval()
				);
			})
			.build("SyncManager " + self);
	}
}
