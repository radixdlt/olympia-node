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

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Objects;

/**
 * A three-chain BFT
 */
public final class ChainedBFT {
	private final NetworkRx network;
	private final PacemakerRx pacemaker;
	private final EventCoordinator eventCoordinator;
	private final Scheduler singleThreadScheduler = Schedulers.single();

	@Inject
	public ChainedBFT(
		EventCoordinator eventCoordinator,
		NetworkRx network,
		PacemakerRx pacemaker
	) {
		Objects.requireNonNull(pacemaker);
		Objects.requireNonNull(network);
		Objects.requireNonNull(eventCoordinator);

		this.pacemaker = pacemaker;
		this.network = network;
		this.eventCoordinator = eventCoordinator;
	}

	// TODO: Add cleanup
	public void start() {
		this.pacemaker.start();

		this.pacemaker.getLocalTimeouts()
			.subscribeOn(this.singleThreadScheduler)
			.subscribe(this.eventCoordinator::processLocalTimeout);

		this.network.getNewRoundMessages()
			.subscribeOn(this.singleThreadScheduler)
			.subscribe(this.eventCoordinator::processRemoteNewRound);

		this.network.getProposalMessages()
			.subscribeOn(this.singleThreadScheduler)
			.subscribe(this.eventCoordinator::processProposal);

		this.network.getVoteMessages()
			.subscribeOn(this.singleThreadScheduler)
			.subscribe(this.eventCoordinator::processVote);
	}
}
