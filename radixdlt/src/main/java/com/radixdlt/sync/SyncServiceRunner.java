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

import com.radixdlt.sync.SyncServiceProcessor.SyncInProgress;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.radixdlt.utils.ThreadFactories;

/**
 * Manages thread safety and is the runner for the Sync Service Processor.
 */
public final class SyncServiceRunner {
	public interface LocalSyncRequestsRx {
		Observable<LocalSyncRequest> localSyncRequests();
	}

	public interface SyncTimeoutsRx {
		Observable<SyncInProgress> timeouts();
	}

	public interface VersionUpdatesRx {
		Observable<Long> versionUpdates();
	}

	private final StateSyncNetwork stateSyncNetwork;
	private final Scheduler singleThreadScheduler;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("SyncManager"));
	private final SyncServiceProcessor syncServiceProcessor;
	private final SyncTimeoutsRx syncTimeoutsRx;
	private final LocalSyncRequestsRx localSyncRequestsRx;
	private final VersionUpdatesRx versionUpdatesRx;
	private final Object lock = new Object();
	private CompositeDisposable compositeDisposable;

	public SyncServiceRunner(
		LocalSyncRequestsRx localSyncRequestsRx,
		SyncTimeoutsRx syncTimeoutsRx,
		VersionUpdatesRx versionUpdatesRx,
		StateSyncNetwork stateSyncNetwork,
		SyncServiceProcessor syncServiceProcessor
	) {
		this.localSyncRequestsRx = Objects.requireNonNull(localSyncRequestsRx);
		this.syncTimeoutsRx = Objects.requireNonNull(syncTimeoutsRx);
		this.versionUpdatesRx = Objects.requireNonNull(versionUpdatesRx);
		this.syncServiceProcessor = Objects.requireNonNull(syncServiceProcessor);
		this.stateSyncNetwork = Objects.requireNonNull(stateSyncNetwork);
		this.singleThreadScheduler = Schedulers.from(this.executorService);
	}

	/**
	 * Start the service
	 */
	public void start() {
		synchronized (lock) {
			if (compositeDisposable != null) {
				return;
			}

			Disposable d0 = stateSyncNetwork.syncRequests()
				.observeOn(singleThreadScheduler)
				.subscribe(syncServiceProcessor::processSyncRequest);

			Disposable d1 = stateSyncNetwork.syncResponses()
				.observeOn(singleThreadScheduler)
				.subscribe(syncServiceProcessor::processSyncResponse);

			Disposable d2 = localSyncRequestsRx.localSyncRequests()
				.observeOn(singleThreadScheduler)
				.subscribe(syncServiceProcessor::processLocalSyncRequest);

			Disposable d3 = syncTimeoutsRx.timeouts()
				.observeOn(singleThreadScheduler)
				.subscribe(syncServiceProcessor::processSyncTimeout);

			Disposable d4 = versionUpdatesRx.versionUpdates()
				.observeOn(singleThreadScheduler)
				.subscribe(syncServiceProcessor::processVersionUpdate);

			compositeDisposable = new CompositeDisposable(d0, d1, d2, d3, d4);
		}
	}

	/**
	 * Stop the service and cleanup resources
	 */
	public void stop() {
		synchronized (lock) {
			if (compositeDisposable != null) {
				compositeDisposable.dispose();
				compositeDisposable = null;
			}
		}
	}
}