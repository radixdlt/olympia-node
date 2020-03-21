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

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;

/**
 * A three-chain BFT
 */
public final class ChainedBFT {
	public enum EventType {
		LOCAL_TIMEOUT,
		NEW_VIEW_MESSAGE,
		PROPOSAL_MESSAGE,
		VOTE_MESSAGE
	}

	public static class Event {
		private final EventType eventType;
		private final Object event;

		private Event(EventType eventType, Object event) {
			this.eventType = eventType;
			this.event = event;
		}

		@Override
		public String toString() {
			return eventType + ": " + event;
		}
	}

	private final EventCoordinatorNetworkRx network;
	private final PacemakerRx pacemaker;
	private final EventCoordinator eventCoordinator;
	private final Scheduler singleThreadScheduler = Schedulers.single();

	@Inject
	public ChainedBFT(
		EventCoordinator eventCoordinator,
		EventCoordinatorNetworkRx network,
		PacemakerRx pacemaker
	) {
		Objects.requireNonNull(pacemaker);
		Objects.requireNonNull(network);
		Objects.requireNonNull(eventCoordinator);

		this.pacemaker = pacemaker;
		this.network = network;
		this.eventCoordinator = eventCoordinator;
	}

	public Observable<Event> processEvents() {
		final Observable<Event> timeouts = this.pacemaker.localTimeouts()
			.subscribeOn(this.singleThreadScheduler)
			.doAfterNext(this.eventCoordinator::processLocalTimeout)
			.map(o -> new Event(EventType.LOCAL_TIMEOUT, o));

		final Observable<Event> newViews = this.network.newViewMessages()
			.subscribeOn(this.singleThreadScheduler)
			.doAfterNext(this.eventCoordinator::processRemoteNewView)
			.map(o -> new Event(EventType.NEW_VIEW_MESSAGE, o));

		final Observable<Event> proposals = this.network.proposalMessages()
			.subscribeOn(this.singleThreadScheduler)
			.doAfterNext(this.eventCoordinator::processProposal)
			.map(o -> new Event(EventType.PROPOSAL_MESSAGE, o));

		final Observable<Event> votes = this.network.voteMessages()
			.subscribeOn(this.singleThreadScheduler)
			.doAfterNext(this.eventCoordinator::processVote)
			.map(o -> new Event(EventType.VOTE_MESSAGE, o));

		return Observable.merge(timeouts, newViews, proposals, votes);
	}
}
