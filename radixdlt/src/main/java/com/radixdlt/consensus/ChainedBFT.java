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
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;
import java.util.concurrent.Executors;

/**
 * A three-chain BFT. The inputs are events via rx streams from a pacemaker and
 * a network.
 */
public final class ChainedBFT {
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

	private final EventCoordinatorNetworkRx network;
	private final PacemakerRx pacemakerRx;
	private final EpochRx epochRx;
	private final EpochManager epochManager;

	private final Scheduler singleThreadScheduler = Schedulers.from(Executors.newSingleThreadExecutor());

	@Inject
	public ChainedBFT(
		EpochRx epochRx,
		EventCoordinatorNetworkRx network,
		PacemakerRx pacemakerRx,
		EpochManager epochManager
	) {
		this.pacemakerRx = Objects.requireNonNull(pacemakerRx);
		this.network = Objects.requireNonNull(network);
		this.epochRx = Objects.requireNonNull(epochRx);
		this.epochManager = Objects.requireNonNull(epochManager);
	}

	private Event processEvent(Object msg, EventCoordinator eventCoordinator) {
		final EventType eventType;
		if (msg instanceof GetVertexRequest) {
			eventCoordinator.processGetVertexRequest((GetVertexRequest) msg);
			return new Event(EventType.GET_VERTEX_REQUEST, msg);
		} else if (msg instanceof View) {
			eventCoordinator.processLocalTimeout((View) msg);
			return new Event(EventType.LOCAL_TIMEOUT, msg);
		} else if (msg instanceof NewView) {
			eventCoordinator.processNewView((NewView) msg);
			eventType = EventType.NEW_VIEW_MESSAGE;
		} else if (msg instanceof Proposal) {
			eventCoordinator.processProposal((Proposal) msg);
			eventType = EventType.PROPOSAL_MESSAGE;
		} else if (msg instanceof Vote) {
			eventCoordinator.processVote((Vote) msg);
			eventType = EventType.VOTE_MESSAGE;
		} else {
			throw new IllegalStateException("Unknown Consensus Message: " + msg);
		}

		return new Event(eventType, msg);
	}


	/**
	 * Returns a cold observable which when subscribed to begins consuming
	 * and processing bft events. Does not begin processing until the observable
	 * is subscribed to.
	 *
	 * @return observable of the events which are being processed
	 */
	public Observable<Event> processEvents() {
		final Observable<ValidatorSet> epochEvents = this.epochRx.epochs()
			.publish()
			.autoConnect(2);

		final Observable<Event> epochs = epochEvents
			.map(o -> new Event(EventType.EPOCH, o));

		final Observable<EventCoordinator> eventCoordinators = epochEvents
			.map(epochManager::nextEpoch)
			.startWithItem(epochManager.start())
			.doOnNext(EventCoordinator::start)
			.replay(1)
			.autoConnect();

		// Need to ensure that first event coordinator is emitted otherwise we may not process
		// initial events due to the .withLatestFrom() drops events
		final Completable firstEventCoordinator = Completable.fromSingle(eventCoordinators.firstOrError());
		final Observable<Object> eventCoordinatorEvents = Observable.merge(
			this.pacemakerRx.localTimeouts().observeOn(this.singleThreadScheduler),
			this.network.consensusEvents().observeOn(this.singleThreadScheduler),
			this.network.rpcRequests().observeOn(this.singleThreadScheduler)
		);
		final Observable<Event> ecMessages = firstEventCoordinator.andThen(
			eventCoordinatorEvents.withLatestFrom(eventCoordinators, this::processEvent)
		);

		return Observable.merge(epochs, ecMessages);
	}
}
