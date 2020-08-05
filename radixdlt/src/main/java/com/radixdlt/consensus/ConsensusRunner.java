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

import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.liveness.PacemakerRx;

import com.radixdlt.utils.ThreadFactories;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Subscription Manager (Start/Stop) to the processing of Consensus events under
 * a single BFT Consensus node instance
 */
public final class ConsensusRunner {
	private static final Logger log = LogManager.getLogger();

	public enum EventType {
		EPOCH_CHANGE,
		LOCAL_TIMEOUT,
		LOCAL_SYNC,
		COMMITTED_STATE_SYNC,
		CONSENSUS_EVENT,
		GET_VERTICES_REQUEST,
		GET_VERTICES_RESPONSE,
		GET_VERTICES_ERROR_RESPONSE,
		GET_EPOCH_REQUEST,
		GET_EPOCH_RESPONSE,
	}

	public static class Event {
		private final EventType eventType;
		private final Object eventObject;

		private Event(EventType eventType, Object eventObject) {
			this.eventType = eventType;
			this.eventObject = eventObject;
		}

		public EventType getEventType() {
			return eventType;
		}

		@Override
		public String toString() {
			return eventType + ": " + eventObject;
		}
	}

	private final ConnectableObservable<Event> events;
	private final Object lock = new Object();
	private final ExecutorService singleThreadExecutor;
	private final Scheduler singleThreadScheduler;
	private Disposable disposable;

	public ConsensusRunner(
		EpochChangeRx epochChangeRx,
		ConsensusEventsRx networkRx,
		PacemakerRx pacemakerRx,
		VertexStoreEventsRx vertexStoreEventsRx,
		CommittedStateSyncRx committedStateSyncRx,
		SyncVerticesRPCRx rpcRx,
		SyncEpochsRPCRx epochsRPCRx,
		EpochManager epochManager
	) {
		this.singleThreadExecutor = Executors.newSingleThreadExecutor(ThreadFactories.daemonThreads("ConsensusRunner"));
		this.singleThreadScheduler = Schedulers.from(this.singleThreadExecutor);

		// It is important that all of these events are executed on the same thread
		// as all logic is dependent on this assumption
		final Observable<Event> eventCoordinatorEvents = Observable.merge(Arrays.asList(
			epochChangeRx.epochChanges()
				.observeOn(singleThreadScheduler)
				.map(e -> {
					epochManager.processEpochChange(e);
					return new Event(EventType.EPOCH_CHANGE, e);
				}),
			pacemakerRx.localTimeouts()
				.observeOn(singleThreadScheduler)
				.map(e -> {
					epochManager.processLocalTimeout(e);
					return new Event(EventType.LOCAL_TIMEOUT, e);
				}),
			networkRx.consensusEvents()
				.observeOn(singleThreadScheduler)
				.map(e -> {
					epochManager.processConsensusEvent(e);
					return new Event(EventType.CONSENSUS_EVENT, e);
				}),
			rpcRx.requests()
				.observeOn(singleThreadScheduler)
				.map(e -> {
					epochManager.processGetVerticesRequest(e);
					return new Event(EventType.GET_VERTICES_REQUEST, e);
				}),
			rpcRx.responses()
				.observeOn(singleThreadScheduler)
				.map(e -> {
					epochManager.processGetVerticesResponse(e);
					return new Event(EventType.GET_VERTICES_RESPONSE, e);
				}),
			rpcRx.errorResponses()
				.observeOn(singleThreadScheduler)
				.map(e -> {
					epochManager.processGetVerticesErrorResponse(e);
					return new Event(EventType.GET_VERTICES_ERROR_RESPONSE, e);
				}),
			epochsRPCRx.epochRequests()
				.observeOn(singleThreadScheduler)
				.map(e -> {
					epochManager.processGetEpochRequest(e);
					return new Event(EventType.GET_EPOCH_REQUEST, e);
				}),
			epochsRPCRx.epochResponses()
				.observeOn(singleThreadScheduler)
				.map(e -> {
					epochManager.processGetEpochResponse(e);
					return new Event(EventType.GET_EPOCH_RESPONSE, e);
				}),
			vertexStoreEventsRx.syncedVertices()
				.observeOn(singleThreadScheduler)
				.map(e -> {
					epochManager.processLocalSync(e);
					return new Event(EventType.LOCAL_SYNC, e);
				}),
			committedStateSyncRx.committedStateSyncs()
				.observeOn(singleThreadScheduler)
				.map(e -> {
					epochManager.processCommittedStateSync(e);
					return new Event(EventType.COMMITTED_STATE_SYNC, e);
				})
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
	public void start() {
		boolean started = false;
		synchronized (lock) {
			if (disposable == null) {
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

	/**
	 * For testing primarily, a way to retrieve events which have been
	 * processed.
	 *
	 * @return hot observable of the events which are being processed
	 */
	public Observable<Event> events() {
		return this.events;
	}
}
