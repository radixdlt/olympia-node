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

package com.radixdlt.integration.distributed;

import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTEventsRx;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.utils.ThreadFactories;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Standalone bft runner without epoch management
 */
public class BFTRunner implements ModuleRunner {
	private static final Logger log = LogManager.getLogger();

	private final ConnectableObservable<Object> events;
	private final Object lock = new Object();
	private final ExecutorService singleThreadExecutor;
	private final Scheduler singleThreadScheduler;
	private final BFTEventProcessor bftEventProcessor;
	private final BFTNode self;
	private Disposable disposable;

	@Inject
	public BFTRunner(
		Observable<LedgerUpdate> ledgerUpdates,
		Set<EventProcessor<LedgerUpdate>> ledgerUpdateProcessors,
		Observable<BFTRebuildUpdate> rebuildUpdates,
		Set<EventProcessor<BFTRebuildUpdate>> rebuildProcessors,
		Observable<BFTInsertUpdate> bftUpdates,
		Set<EventProcessor<BFTInsertUpdate>> bftUpdateProcessors,
		Observable<VertexRequestTimeout> bftSyncTimeouts,
		Set<EventProcessor<VertexRequestTimeout>> vertexRequestTimeoutProcessors,
		Observable<ViewUpdate> viewUpdates,
		Set<EventProcessor<ViewUpdate>> viewUpdateProcessors,
		Observable<ScheduledLocalTimeout> timeouts,
		Set<EventProcessor<ScheduledLocalTimeout>> timeoutProcessors,
		Flowable<RemoteEvent<GetVerticesRequest>> verticesRequests,
		Set<RemoteEventProcessor<GetVerticesRequest>> requestProcessors,
		BFTEventsRx networkRx,
		SyncVerticesRPCRx rpcRx,
		BFTEventProcessor bftEventProcessor,
		BFTSync vertexStoreSync,
		@Self BFTNode self
	) {
		this.bftEventProcessor = Objects.requireNonNull(bftEventProcessor);
		this.singleThreadExecutor = Executors.newSingleThreadExecutor(ThreadFactories.daemonThreads("ConsensusRunner " + self));
		this.singleThreadScheduler = Schedulers.from(this.singleThreadExecutor);
		this.self = Objects.requireNonNull(self);

		// It is important that all of these events are executed on the same thread
		// as all logic is dependent on this assumption
		final Observable<Object> eventCoordinatorEvents = Observable.merge(Arrays.asList(
			timeouts
				.observeOn(singleThreadScheduler)
				.doOnNext(t -> timeoutProcessors.forEach(p -> p.process(t))),
			viewUpdates
				.observeOn(singleThreadScheduler)
				.doOnNext(v -> viewUpdateProcessors.forEach(p -> p.process(v))),
			networkRx.localBftEvents().toObservable()
				.observeOn(singleThreadScheduler)
				.doOnNext(this::processConsensusEvent),
			networkRx.remoteBftEvents().toObservable()
				.observeOn(singleThreadScheduler)
				.doOnNext(this::processConsensusEvent),
			verticesRequests.toObservable()
				.observeOn(singleThreadScheduler)
				.doOnNext(r -> requestProcessors.forEach(p -> p.process(r.getOrigin(), r.getEvent()))),
			rpcRx.responses().toObservable()
				.observeOn(singleThreadScheduler)
				.doOnNext(resp -> vertexStoreSync.responseProcessor().process(resp)),
			rpcRx.errorResponses().toObservable()
				.observeOn(singleThreadScheduler)
				.doOnNext(vertexStoreSync::processGetVerticesErrorResponse),
			bftUpdates
				.observeOn(singleThreadScheduler)
				.doOnNext(update -> bftUpdateProcessors.forEach(p -> p.process(update))),
			rebuildUpdates
				.observeOn(singleThreadScheduler)
				.doOnNext(update -> rebuildProcessors.forEach(p -> p.process(update))),
			ledgerUpdates
				.observeOn(singleThreadScheduler)
				.doOnNext(update -> ledgerUpdateProcessors.forEach(p -> p.process(update))),
			bftSyncTimeouts
				.observeOn(singleThreadScheduler)
				.doOnNext(t -> vertexRequestTimeoutProcessors.forEach(p -> p.process(t)))
		));

		this.events = eventCoordinatorEvents
			.doOnError(e -> {
				// TODO: Implement better error handling especially against Byzantine nodes.
				// TODO: Exit process for now.
				log.fatal(String.format("%s: Unexpected exception occurred", self), e);
				System.exit(-1);
			})
			.publish();
	}

	private void processConsensusEvent(ConsensusEvent e) {
		if (e instanceof Proposal) {
			bftEventProcessor.processProposal((Proposal) e);
		} else if (e instanceof Vote) {
			bftEventProcessor.processVote((Vote) e);
		} else {
			throw new IllegalStateException(self + ": Unknown consensus event: " + e);
		}
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
				singleThreadExecutor.submit(bftEventProcessor::start);
				disposable = this.events.connect();
				started = true;
			}
		}
		if (started) {
			log.info("{}: Consensus started", this.self);
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
			log.info("{}: Consensus stopped", this.self);
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
