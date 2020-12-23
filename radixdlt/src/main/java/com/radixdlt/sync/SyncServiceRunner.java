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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.ModuleRunner;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessWithSyncRunner;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncInProgress;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.radixdlt.utils.ThreadFactories;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages thread safety and is the runner for the Sync Service Processor.
 */
@Singleton
public final class SyncServiceRunner<T extends LedgerUpdate> implements ModuleRunner {
	private static final Logger log = LogManager.getLogger();

	private final Scheduler singleThreadScheduler;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
		ThreadFactories.daemonThreads("SyncManager")
	);
	private final EventProcessor<LocalSyncRequest> syncRequestEventProcessor;

	private final RemoteEventProcessor<DtoLedgerHeaderAndProof> remoteSyncServiceProcessor;
	private final RemoteEventProcessor<DtoCommandsAndProof> responseProcessor;

	private final Observable<SyncInProgress> syncTimeouts;
	private final EventProcessor<SyncInProgress> syncTimeoutProcessor;

	private final Observable<LocalSyncRequest> localSyncRequests;
	private final Observable<T> ledgerUpdates;
	private final Set<EventProcessor<T>> ledgerUpdateProcessors;

	private final Observable<RemoteEvent<DtoCommandsAndProof>> remoteSyncResponses;
	private final Observable<RemoteEvent<DtoLedgerHeaderAndProof>> remoteSyncRequests;
	private final Object lock = new Object();
	private CompositeDisposable compositeDisposable;

	@Inject
	public SyncServiceRunner(
		Observable<LocalSyncRequest> localSyncRequests,
		EventProcessor<LocalSyncRequest> syncRequestEventProcessor,
		Observable<SyncInProgress> syncTimeouts,
		EventProcessor<SyncInProgress> syncTimeoutProcessor,
		Observable<T> ledgerUpdates,
		@ProcessWithSyncRunner Set<EventProcessor<T>> ledgerUpdateProcessors,
		Observable<RemoteEvent<DtoLedgerHeaderAndProof>> remoteSyncRequests,
		RemoteEventProcessor<DtoLedgerHeaderAndProof> remoteSyncServiceProcessor,
		Observable<RemoteEvent<DtoCommandsAndProof>> remoteSyncResponses,
		RemoteEventProcessor<DtoCommandsAndProof> responseProcessor
	) {
		this.localSyncRequests = Objects.requireNonNull(localSyncRequests);
		this.syncRequestEventProcessor = Objects.requireNonNull(syncRequestEventProcessor);

		this.remoteSyncRequests = Objects.requireNonNull(remoteSyncRequests);
		this.remoteSyncResponses = Objects.requireNonNull(remoteSyncResponses);

		this.syncTimeouts = Objects.requireNonNull(syncTimeouts);
		this.syncTimeoutProcessor = Objects.requireNonNull(syncTimeoutProcessor);

		this.ledgerUpdates = Objects.requireNonNull(ledgerUpdates);
		this.singleThreadScheduler = Schedulers.from(this.executorService);
		this.remoteSyncServiceProcessor = Objects.requireNonNull(remoteSyncServiceProcessor);
		this.responseProcessor = Objects.requireNonNull(responseProcessor);
		this.ledgerUpdateProcessors = Objects.requireNonNull(ledgerUpdateProcessors);
	}

	/**
	 * Start the service
	 */
	@Override
	public void start() {
		synchronized (lock) {
			if (compositeDisposable != null) {
				return;
			}

			Disposable d0 = remoteSyncRequests
				.observeOn(singleThreadScheduler)
				.subscribe(e -> remoteSyncServiceProcessor.process(e.getOrigin(), e.getEvent()));

			Disposable d1 = remoteSyncResponses
				.observeOn(singleThreadScheduler)
				.subscribe(e -> responseProcessor.process(e.getOrigin(), e.getEvent()));

			Disposable d2 = localSyncRequests
				.observeOn(singleThreadScheduler)
				.subscribe(syncRequestEventProcessor::process);

			Disposable d3 = syncTimeouts
				.observeOn(singleThreadScheduler)
				.subscribe(syncTimeoutProcessor::process);

			Disposable d4 = ledgerUpdates
				.observeOn(singleThreadScheduler)
				.subscribe(u -> ledgerUpdateProcessors.forEach(e -> e.process(u)));

			compositeDisposable = new CompositeDisposable(d0, d1, d2, d3, d4);
		}

		log.info("Sync started");
	}

	/**
	 * Stop the service and cleanup resources
	 */
	@Override
	public void stop() {
		synchronized (lock) {
			if (compositeDisposable != null) {
				compositeDisposable.dispose();
				compositeDisposable = null;
			}
		}
	}
}