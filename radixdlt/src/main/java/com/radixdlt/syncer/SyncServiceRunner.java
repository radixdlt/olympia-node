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

package com.radixdlt.syncer;

import com.radixdlt.syncer.SyncServiceProcessor.SyncInProgress;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.radixdlt.utils.ThreadFactories;

public final class SyncServiceRunner {
	public interface LocalSyncRequestsRx {
		Observable<LocalSyncRequest> localSyncRequests();
	}

	public interface SyncTimeoutsRx {
		Observable<SyncInProgress> timeouts();
	}

	private final StateSyncNetwork stateSyncNetwork;
	private final Scheduler singleThreadScheduler;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("SyncManager"));
	private final SyncServiceProcessor syncServiceProcessor;
	private final SyncTimeoutsRx syncTimeoutsRx;
	private final LocalSyncRequestsRx localSyncRequestsRx;

	public SyncServiceRunner(
		LocalSyncRequestsRx localSyncRequestsRx,
		SyncTimeoutsRx syncTimeoutsRx,
		StateSyncNetwork stateSyncNetwork,
		SyncServiceProcessor syncServiceProcessor
	) {
		this.localSyncRequestsRx = Objects.requireNonNull(localSyncRequestsRx);
		this.syncTimeoutsRx = Objects.requireNonNull(syncTimeoutsRx);
		this.syncServiceProcessor = Objects.requireNonNull(syncServiceProcessor);
		this.stateSyncNetwork = Objects.requireNonNull(stateSyncNetwork);
		this.singleThreadScheduler = Schedulers.from(this.executorService);
	}

	/**
	 * Start the service
	 */
	public void start() {
		stateSyncNetwork.syncRequests()
			.observeOn(singleThreadScheduler)
			.subscribe(syncServiceProcessor::processSyncRequest);

		stateSyncNetwork.syncResponses()
			.observeOn(singleThreadScheduler)
			.subscribe(syncServiceProcessor::processSyncResponse);

		localSyncRequestsRx.localSyncRequests()
			.observeOn(singleThreadScheduler)
			.subscribe(syncServiceProcessor::processLocalSyncRequest);

		syncTimeoutsRx.timeouts()
			.observeOn(singleThreadScheduler)
			.subscribe(syncServiceProcessor::processSyncTimeout);
	}

	public void close() {
		executorService.shutdown();
	}
}