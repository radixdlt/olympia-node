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
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.epoch.Epoched;

import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.utils.ThreadFactories;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Subscription Manager (Start/Stop) to the processing of Consensus events under
 * a single BFT Consensus node instance
 */
public final class EpochManagerRunner implements ModuleRunner {
	private static final Logger log = LogManager.getLogger();
	private final Object lock = new Object();
	private final ExecutorService singleThreadExecutor;
	private final Scheduler singleThreadScheduler;
	private final EpochManager epochManager;
	private final List<Subscription<?>> subscriptions;

	private Disposable disposable;

	@Inject
	public EpochManagerRunner(
		@Self BFTNode self,
		Observable<EpochsLedgerUpdate> ledgerUpdates,
		EventProcessor<EpochsLedgerUpdate> epochsLedgerUpdateEventProcessor,
		Observable<BFTInsertUpdate> bftUpdates,
		EventProcessor<BFTInsertUpdate> bftUpdateProcessor,
		Observable<BFTRebuildUpdate> bftRebuilds,
		EventProcessor<BFTRebuildUpdate> bftRebuildProcessor,
		Observable<VertexRequestTimeout> bftSyncTimeouts,
		EventProcessor<VertexRequestTimeout> vertexRequestTimeoutEventProcessor,
		Observable<EpochViewUpdate> localViewUpdates,
		EventProcessor<EpochViewUpdate> epochViewUpdateEventProcessor,
		Flowable<RemoteEvent<GetVerticesRequest>> verticesRequests,
		Flowable<RemoteEvent<GetVerticesResponse>> verticesResponses,
		Flowable<RemoteEvent<GetVerticesErrorResponse>> bftSyncErrorResponses,
		BFTEventsRx networkRx,
		Observable<Epoched<ScheduledLocalTimeout>> timeouts,
		EpochManager epochManager
	) {
		this.epochManager = Objects.requireNonNull(epochManager);
		this.singleThreadExecutor = Executors.newSingleThreadExecutor(ThreadFactories.daemonThreads("ConsensusRunner " + self));
		this.singleThreadScheduler = Schedulers.from(this.singleThreadExecutor);

		this.subscriptions = List.of(
			new Subscription<>(ledgerUpdates, epochsLedgerUpdateEventProcessor::process, singleThreadScheduler),
			new Subscription<>(bftUpdates, bftUpdateProcessor::process, singleThreadScheduler),
			new Subscription<>(bftRebuilds, bftRebuildProcessor::process, singleThreadScheduler),
			new Subscription<>(bftSyncTimeouts, vertexRequestTimeoutEventProcessor::process, singleThreadScheduler),
			new Subscription<>(localViewUpdates, epochViewUpdateEventProcessor::process, singleThreadScheduler),
			new Subscription<>(timeouts, epochManager::processLocalTimeout, singleThreadScheduler),
			new Subscription<>(networkRx.localBftEvents(), epochManager::processConsensusEvent, singleThreadScheduler),
			new Subscription<>(networkRx.remoteBftEvents(), epochManager::processConsensusEvent, singleThreadScheduler),
			new Subscription<>(
				verticesRequests,
				req -> epochManager.bftSyncRequestProcessor().process(req.getOrigin(), req.getEvent()),
				singleThreadScheduler
			),
			new Subscription<>(
				verticesResponses,
				resp -> epochManager.bftSyncResponseProcessor().process(resp.getOrigin(), resp.getEvent()),
				singleThreadScheduler
			),
			new Subscription<>(
				bftSyncErrorResponses,
				resp -> epochManager.bftSyncErrorResponseProcessor().process(resp.getOrigin(), resp.getEvent()),
				singleThreadScheduler
			)
		);
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

				@SuppressWarnings("unchecked")
				final var disposables = this.subscriptions.stream()
					.map(s -> (Subscription<Object>) s) // seriously, Java?
					.map(Subscription::subscribe)
					.collect(Collectors.toList());

				this.disposable = new CompositeDisposable(disposables);
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

	private static class Subscription<T> {
		final Flowable<T> flowable;
		final Observable<T> observable;
		final Consumer<T> consumer;
		private final Scheduler scheduler;

		Subscription(Flowable<T> flowable, Consumer<T> consumer, Scheduler scheduler) {
			this.flowable = flowable;
			this.observable = null;
			this.consumer = consumer;
			this.scheduler = scheduler;
		}

		Subscription(Observable<T> observable, Consumer<T> consumer, Scheduler scheduler) {
			this.flowable = null;
			this.observable = observable;
			this.consumer = consumer;
			this.scheduler = scheduler;
		}

		public Disposable subscribe() {
			if (flowable != null) {
				return flowable
					.observeOn(scheduler)
					.subscribe(consumer, e -> {
						// TODO: Implement better error handling especially against Byzantine nodes.
						// TODO: Exit process for now.
						log.error("Unexpected exception occurred", e);
						e.printStackTrace();
						Thread.sleep(1000);
						System.exit(-1);
					});
			} else {
				return observable
					.observeOn(scheduler)
					.subscribe(consumer, e -> {
						// TODO: Implement better error handling especially against Byzantine nodes.
						// TODO: Exit process for now.
						log.error("Unexpected exception occurred", e);
						e.printStackTrace();
						Thread.sleep(1000);
						System.exit(-1);
					});
			}
		}
	}
}
