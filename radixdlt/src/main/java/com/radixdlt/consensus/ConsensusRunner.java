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

import com.radixdlt.consensus.liveness.PacemakerRx;

import com.radixdlt.consensus.validators.ValidatorSet;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.Executors;

/**
 * Subscription Manager (Start/Stop) to the processing of Consensus events under
 * a single BFT Consensus node instance
 */
public final class ConsensusRunner {
	public enum EventType {
		EPOCH,
		LOCAL_TIMEOUT,
		NEW_VIEW_MESSAGE,
		PROPOSAL_MESSAGE,
		VOTE_MESSAGE,
		GET_VERTEX_REQUEST,
	}

	public static class Event {
		private final EventType eventType;
		private final Object eventObject;

		private Event(EventType eventType, Object eventObject) {
			this.eventType = eventType;
			this.eventObject = eventObject;
		}

		@Override
		public String toString() {
			return eventType + ": " + eventObject;
		}
	}

	private final ConnectableObservable<Event> events;
	private final Object lock = new Object();
	private Disposable disposable;

	@Inject
	public ConsensusRunner(
		EpochRx epochRx,
		EventCoordinatorNetworkRx network,
		PacemakerRx pacemakerRx,
		EpochManager epochManager
	) {
		final Scheduler singleThreadScheduler = Schedulers.from(Executors.newSingleThreadExecutor());
		final Observable<ValidatorSet> epochEvents = epochRx.epochs()
			.publish()
			.autoConnect(2);

		final Observable<Event> epochs = epochEvents
			.map(o -> new Event(EventType.EPOCH, o));

		final Observable<BFTEventProcessor> eventCoordinators = epochEvents
			.observeOn(singleThreadScheduler)
			.map(epochManager::nextEpoch)
			.startWithItem(epochManager.start())
			.doOnNext(BFTEventProcessor::start)
			.replay(1)
			.autoConnect();

		// Need to ensure that first event coordinator is emitted otherwise we may not process
		// initial events due to the .withLatestFrom() drops events
		final Completable firstEventCoordinator = Completable.fromSingle(eventCoordinators.firstOrError());
		final Observable<Object> eventCoordinatorEvents = Observable.merge(
			pacemakerRx.localTimeouts().observeOn(singleThreadScheduler),
			network.consensusEvents().observeOn(singleThreadScheduler),
			network.rpcRequests().observeOn(singleThreadScheduler)
		);
		final Observable<Event> ecMessages = firstEventCoordinator.andThen(
			eventCoordinatorEvents.withLatestFrom(eventCoordinators, this::processEvent)
		);

		this.events = Observable.merge(epochs, ecMessages).publish();
	}

	private Event processEvent(Object msg, BFTEventProcessor processor) {
		final EventType eventType;
		if (msg instanceof GetVertexRequest) {
			processor.processGetVertexRequest((GetVertexRequest) msg);
			return new Event(EventType.GET_VERTEX_REQUEST, msg);
		} else if (msg instanceof View) {
			processor.processLocalTimeout((View) msg);
			return new Event(EventType.LOCAL_TIMEOUT, msg);
		} else if (msg instanceof NewView) {
			processor.processNewView((NewView) msg);
			eventType = EventType.NEW_VIEW_MESSAGE;
		} else if (msg instanceof Proposal) {
			processor.processProposal((Proposal) msg);
			eventType = EventType.PROPOSAL_MESSAGE;
		} else if (msg instanceof Vote) {
			processor.processVote((Vote) msg);
			eventType = EventType.VOTE_MESSAGE;
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
	 * For testing primarily, a way to retrieve events which have been
	 * processed.
	 *
	 * @return hot observable of the events which are being processed
	 */
	public Observable<Event> events() {
		return this.events;
	}
}
