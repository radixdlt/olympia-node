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

package com.radixdlt.mempool;

import com.google.common.collect.ImmutableList;
import com.google.inject.Singleton;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.network.addressbook.PeersView;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

/**
 * Relays commands from the local mempool to node neighbors.
 */
@Singleton
public final class MempoolRelayer {
	private final PeersView peersView;
	private final RemoteEventDispatcher<MempoolAdd> remoteEventDispatcher;
	private final MempoolConfig mempoolConfig;
	private final SystemCounters counters;

	@Inject
	public MempoolRelayer(
		RemoteEventDispatcher<MempoolAdd> remoteEventDispatcher,
		PeersView peersView,
		MempoolConfig mempoolConfig,
		SystemCounters counters
	) {
		this.remoteEventDispatcher = Objects.requireNonNull(remoteEventDispatcher);
		this.peersView = Objects.requireNonNull(peersView);
		this.mempoolConfig = Objects.requireNonNull(mempoolConfig);
		this.counters = Objects.requireNonNull(counters);
	}

	public EventProcessor<MempoolAddSuccess> mempoolAddSuccessEventProcessor() {
		return mempoolAddSuccess -> {
			final var ignorePeers = mempoolAddSuccess.getOrigin()
				.map(ImmutableList::of)
				.orElse(ImmutableList.of());
			relayCommands(ImmutableList.of(mempoolAddSuccess.getCommand()), ignorePeers);
		};
	}

	public EventProcessor<MempoolRelayCommands> mempoolRelayCommandsEventProcessor() {
		return mempoolRelayCommands -> relayCommands(mempoolRelayCommands.getCommands(), ImmutableList.of());
	}

	private void relayCommands(ImmutableList<Command> commands, ImmutableList<BFTNode> ignorePeers) {
		final var mempoolAddMsg = MempoolAdd.create(commands);
		final var peers = new ArrayList<>(this.peersView.peers());
		peers.removeAll(ignorePeers);
		Collections.shuffle(peers);
		peers.stream()
			.limit(mempoolConfig.relayMaxPeers())
			.forEach(peer -> {
				counters.add(CounterType.MEMPOOL_RELAYER_SENT_COUNT, commands.size());
				this.remoteEventDispatcher.dispatch(peer, mempoolAddMsg);
			});
	}
}
