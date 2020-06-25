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

import com.google.inject.Inject;

import com.radixdlt.consensus.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.liveness.PacemakerRx;

import com.radixdlt.crypto.Hash;
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
		EPOCH,
		VALIDATOR_SET,
		LOCAL_TIMEOUT,
		LOCAL_SYNC,
		COMMITTED_STATE_SYNC,
		NEW_VIEW_MESSAGE,
		PROPOSAL_MESSAGE,
		VOTE_MESSAGE,
		GET_VERTICES_REQUEST,
		GET_VERTICES_RESPONSE,
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
	private final EpochManager epochManager;

	@Inject
	public ConsensusRunner(
		EpochChangeRx epochChangeRx,
		EventCoordinatorNetworkRx networkRx,
		PacemakerRx pacemakerRx,
		LocalSyncRx localSyncRx,
		CommittedStateSyncRx committedStateSyncRx,
		SyncVerticesRPCRx rpcRx,
		EpochManager epochManager
	) {
		this.singleThreadExecutor = Executors.newSingleThreadExecutor(ThreadFactories.daemonThreads("ConsensusRunner"));
		this.singleThreadScheduler = Schedulers.from(this.singleThreadExecutor);
		this.epochManager = epochManager;

		final Observable<Object> eventCoordinatorEvents = Observable.merge(Arrays.asList(
			epochChangeRx.epochChanges().observeOn(singleThreadScheduler),
			pacemakerRx.localTimeouts().observeOn(singleThreadScheduler),
			networkRx.consensusEvents().observeOn(singleThreadScheduler),
			rpcRx.requests().observeOn(singleThreadScheduler),
			rpcRx.responses().observeOn(singleThreadScheduler),
			localSyncRx.localSyncs().observeOn(singleThreadScheduler),
			committedStateSyncRx.committedStateSyncs().observeOn(singleThreadScheduler)
		));
		final Observable<Event> ecMessages = eventCoordinatorEvents.map(this::processEvent);

		this.events = ecMessages
			.doOnError(e -> {
				// TODO: Implement better error handling especially against Byzantine nodes.
				// TODO: Exit process for now.
				log.error("Unexpected occurred", e);
				System.exit(-1);
			})
			.publish();
	}

	// TODO: Cleanup
	private Event processEvent(Object msg) {
		final EventType eventType;
		if (msg instanceof EpochChange) {
			epochManager.processEpochChange((EpochChange) msg);
			return new Event(EventType.EPOCH, msg);
		} else if (msg instanceof GetVerticesRequest) {
			epochManager.processGetVerticesRequest((GetVerticesRequest) msg);
			return new Event(EventType.GET_VERTICES_REQUEST, msg);
		} else if (msg instanceof GetVerticesResponse) {
			epochManager.processGetVerticesResponse((GetVerticesResponse) msg);
			return new Event(EventType.GET_VERTICES_RESPONSE, msg);
		} else if (msg instanceof View) {
			epochManager.processLocalTimeout((View) msg);
			return new Event(EventType.LOCAL_TIMEOUT, msg);
		} else if (msg instanceof NewView) {
			epochManager.processConsensusEvent((NewView) msg);
			eventType = EventType.NEW_VIEW_MESSAGE;
		} else if (msg instanceof Proposal) {
			epochManager.processConsensusEvent((Proposal) msg);
			eventType = EventType.PROPOSAL_MESSAGE;
		} else if (msg instanceof Vote) {
			epochManager.processConsensusEvent((Vote) msg);
			eventType = EventType.VOTE_MESSAGE;
		} else if (msg instanceof Hash) {
			epochManager.processLocalSync((Hash) msg);
			eventType = EventType.LOCAL_SYNC;
		} else if (msg instanceof CommittedStateSync) {
			epochManager.processCommittedStateSync((CommittedStateSync) msg);
			eventType = EventType.COMMITTED_STATE_SYNC;
		} else {
			throw new IllegalStateException("Unknown Consensus Message: " + msg);
		}

		return new Event(eventType, msg);
	}

	/**
	 * Starts processing events. This call is idempotent in that multiple
	 * calls will not affect execution, only one event handling stream will ever
	 * occur.
	 */
	public void start() {
		synchronized (lock) {
			if (disposable == null) {
				disposable = this.events.connect();
			}
		}
	}

	/**
	 * Stop processing events.
	 */
	public void stop() {
		synchronized (lock) {
			if (disposable != null) {
				disposable.dispose();
				disposable = null;
			}
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
