/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Overly simplistic network implementation that just sends messages to itself.
 */
public class DumbNetwork implements NetworkSender, NetworkRx {
	public static final int LOOPBACK_DELAY = 100;
	private final AtomicReference<Consumer<Vertex>> proposalCallbackRef;
	private final AtomicReference<Consumer<NewRound>> newRoundCallbackRef;
	private final AtomicReference<Consumer<Vote>> voteCallbackRef;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

	public DumbNetwork() {
		this.proposalCallbackRef = new AtomicReference<>();
		this.newRoundCallbackRef = new AtomicReference<>();
		this.voteCallbackRef = new AtomicReference<>();
	}

	@Override
	public void broadcastProposal(Vertex vertex) {
		executorService.schedule(() -> {
			Consumer<Vertex> callback = this.proposalCallbackRef.get();
			if (callback != null) {
				callback.accept(vertex);
			}
		}, LOOPBACK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void sendNewRound(NewRound newRound) {
		executorService.schedule(() -> {
			Consumer<NewRound> callback = this.newRoundCallbackRef.get();
			if (callback != null) {
				callback.accept(newRound);
			}
		}, LOOPBACK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void sendVote(Vote vote) {
		executorService.schedule(() -> {
			Consumer<Vote> callback = this.voteCallbackRef.get();
			if (callback != null) {
				callback.accept(vote);
			}
		}, LOOPBACK_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	public void addReceiveProposalCallback(Consumer<Vertex> callback) {
		this.proposalCallbackRef.set(callback);
	}

	@Override
	public void addReceiveNewRoundCallback(Consumer<NewRound> callback) {
		this.newRoundCallbackRef.set(callback);
	}

	@Override
	public void addReceiveVoteCallback(Consumer<Vote> callback) {
		this.voteCallbackRef.set(callback);
	}
}
