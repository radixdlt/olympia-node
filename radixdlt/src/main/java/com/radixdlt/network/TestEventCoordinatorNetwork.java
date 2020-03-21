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

package com.radixdlt.network;

import com.radixdlt.common.EUID;
import com.radixdlt.consensus.EventCoordinatorNetworkRx;
import com.radixdlt.consensus.EventCoordinatorNetworkSender;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.Vote;

/**
 * Overly simplistic network implementation that just sends messages to itself.
 */
public class TestEventCoordinatorNetwork implements EventCoordinatorNetworkSender {
	public static final int LOOPBACK_DELAY = 50;
	private final PublishSubject<Vertex> proposals;
	private final PublishSubject<Map.Entry<NewView, EUID>> newViews;
	private final PublishSubject<Map.Entry<Vote, EUID>> votes;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

	public TestEventCoordinatorNetwork() {
		this.proposals = PublishSubject.create();
		this.newViews = PublishSubject.create();
		this.votes = PublishSubject.create();
	}

	@Override
	public void broadcastProposal(Vertex vertex) {
		executorService.schedule(() -> {
			this.proposals.onNext(vertex);
		}, LOOPBACK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void sendNewView(NewView newView, EUID newViewLeader) {
		executorService.schedule(() -> {
			this.newViews.onNext(new SimpleEntry<>(newView, newViewLeader));
		}, LOOPBACK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void sendVote(Vote vote, EUID leader) {
		executorService.schedule(() -> {
			this.votes.onNext(new SimpleEntry<>(vote, leader));
		}, LOOPBACK_DELAY, TimeUnit.MILLISECONDS);
	}

	public EventCoordinatorNetworkRx getNetworkRx(EUID euid) {
		return new EventCoordinatorNetworkRx() {
			@Override
			public Observable<Vertex> proposalMessages() {
				return proposals;
			}

			@Override
			public Observable<NewView> newViewMessages() {
				return newViews
					.filter(e -> e.getValue().equals(euid))
					.map(Entry::getKey);
			}

			@Override
			public Observable<Vote> voteMessages() {
				return votes
					.filter(e -> e.getValue().equals(euid))
					.map(Entry::getKey);
			}
		};
	}
}
