/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.epoch.LocalViewUpdate;
import com.radixdlt.consensus.liveness.PacemakerRx;

import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.utils.ThreadFactories;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Subscription Manager (Start/Stop) to the processing of Consensus events under
 * a single BFT Consensus node instance
 */
public final class EpochManagerRunner implements ModuleRunner {
	private static final Logger log = LogManager.getLogger();
	private final ConnectableObservable<Object> events;
	private final Object lock = new Object();
	private final ExecutorService singleThreadExecutor;
	private final Scheduler singleThreadScheduler;
	private final EpochManager epochManager;
	private Disposable disposable;

	@Inject
	public EpochManagerRunner(
		Observable<EpochsLedgerUpdate> ledgerUpdates,
		Observable<BFTUpdate> bftUpdates,
		Observable<LocalGetVerticesRequest> bftSyncTimeouts,
		Observable<LocalViewUpdate> localViewUpdates,
		BFTEventsRx networkRx,
		PacemakerRx pacemakerRx,
		SyncVerticesRPCRx rpcRx,
		SyncEpochsRPCRx epochsRPCRx,
		EpochManager epochManager
	) {
		this.epochManager = Objects.requireNonNull(epochManager);
		this.singleThreadExecutor = Executors.newSingleThreadExecutor(ThreadFactories.daemonThreads("ConsensusRunner"));
		this.singleThreadScheduler = Schedulers.from(this.singleThreadExecutor);

		// It is important that all of these events are executed on the same thread
		// as all logic is dependent on this assumption
		final Observable<Object> eventCoordinatorEvents = Observable.merge(Arrays.asList(
			ledgerUpdates
				.observeOn(singleThreadScheduler)
				.doOnNext(epochManager::processLedgerUpdate),
			bftUpdates
					.observeOn(singleThreadScheduler)
					.doOnNext(epochManager::processBFTUpdate),
			bftSyncTimeouts
					.observeOn(singleThreadScheduler)
					.doOnNext(epochManager::processGetVerticesLocalTimeout),
			localViewUpdates
				.observeOn(singleThreadScheduler)
				.doOnNext(epochManager::processLocalViewUpdate),
			pacemakerRx.localTimeouts()
				.observeOn(singleThreadScheduler)
				.doOnNext(epochManager::processLocalTimeout),
			networkRx.bftEvents()
				.observeOn(singleThreadScheduler)
				.doOnNext(epochManager::processConsensusEvent),
			rpcRx.requests()
				.observeOn(singleThreadScheduler)
				.doOnNext(epochManager::processGetVerticesRequest),
			rpcRx.responses()
				.observeOn(singleThreadScheduler)
				.doOnNext(epochManager::processGetVerticesResponse),
			rpcRx.errorResponses()
				.observeOn(singleThreadScheduler)
				.doOnNext(epochManager::processGetVerticesErrorResponse),
			epochsRPCRx.epochRequests()
				.observeOn(singleThreadScheduler)
				.doOnNext(epochManager::processGetEpochRequest),
			epochsRPCRx.epochResponses()
				.observeOn(singleThreadScheduler)
				.doOnNext(epochManager::processGetEpochResponse)
		));

		this.events = eventCoordinatorEvents
			.doOnError(e -> {
				// TODO: Implement better error handling especially against Byzantine nodes.
				// TODO: Exit process for now.
				log.error("Unexpected exception occurred", e);
				System.exit(-1);
			})
			.publish();
	}

	/**
	 * Starts processing events. This call is idempotent in that multiple
	 * calls will not affect execution, only one event handling stream will ever
	 * occur.
	 */
	@Override
	public void start() {
		boolean started = false;
		synchronized (lock) {
			if (disposable == null) {
				singleThreadExecutor.submit(epochManager::start);
				disposable = this.events.connect();
				started = true;
			}
		}
		if (started) {
			log.info("Consensus started");
		}
	}

	/**
	 * Stop processing events.
	 */
	@Override
	public void stop() {
		boolean stopped = false;
		synchronized (lock) {
			if (disposable != null) {
				disposable.dispose();
				disposable = null;
				stopped = true;
			}
		}
		if (stopped) {
			log.info("Consensus stopped");
		}
	}

	/**
	 * Terminate and stop all threads.
	 * The runner cannot be restarted once this method is called.
	 */
	public void shutdown() {
		synchronized (lock) {
			stop();
			this.singleThreadScheduler.shutdown(); // Doesn't appear to do much
			this.singleThreadExecutor.shutdown();
			try {
				this.singleThreadExecutor.awaitTermination(10L, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// Not handling this here
				Thread.currentThread().interrupt();
			}
		}
	}
}

