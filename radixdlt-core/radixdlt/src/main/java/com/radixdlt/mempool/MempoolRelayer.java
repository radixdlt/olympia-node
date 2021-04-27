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
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.network.p2p.PeersView;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Relays commands from the local mempool to node neighbors.
 */
@Singleton
public final class MempoolRelayer {
	private final PeersView peersView;
	private final RemoteEventDispatcher<MempoolAdd> remoteEventDispatcher;
	private final SystemCounters counters;
	private final Mempool<?> mempool;
	private final long initialDelay;
	private final long repeatDelay;
	private final int maxPeers;

	@Inject
	public MempoolRelayer(
		Mempool<?> mempool,
		RemoteEventDispatcher<MempoolAdd> remoteEventDispatcher,
		PeersView peersView,
		@MempoolRelayInitialDelay long initialDelay,
		@MempoolRelayRepeatDelay long repeatDelay,
		@MempoolRelayMaxPeers int maxPeers,
		SystemCounters counters
	) {
		this.mempool = mempool;
		this.remoteEventDispatcher = Objects.requireNonNull(remoteEventDispatcher);
		this.peersView = Objects.requireNonNull(peersView);
		this.initialDelay = initialDelay;
		this.repeatDelay = repeatDelay;
		this.maxPeers = maxPeers;
		this.counters = Objects.requireNonNull(counters);
	}

	public EventProcessor<MempoolAddSuccess> mempoolAddSuccessEventProcessor() {
		return mempoolAddSuccess -> {
			final var ignorePeers = mempoolAddSuccess.getOrigin()
				.map(ImmutableList::of)
				.orElse(ImmutableList.of());
			relayCommands(ImmutableList.of(mempoolAddSuccess.getTxn()), ignorePeers);
		};
	}

	public EventProcessor<MempoolRelayTrigger> mempoolRelayTriggerEventProcessor() {
		return ev -> {
			final var now = System.currentTimeMillis();
			final var maxAddTime = now - initialDelay;
			final var txns = mempool.scanUpdateAndGet(
				m -> m.getInserted() <= maxAddTime
					&& now >= m.getLastRelayed().orElse(0L)
					+ repeatDelay,
				m -> m.setLastRelayed(now)
			);
			if (!txns.isEmpty()) {
				relayCommands(txns, ImmutableList.of());
			}
		};
	}

	private void relayCommands(List<Txn> txns, ImmutableList<BFTNode> ignorePeers) {
		final var mempoolAddMsg = MempoolAdd.create(txns);
		final var peers = this.peersView.peers()
			.map(PeersView.PeerInfo::bftNode)
			.collect(Collectors.toList());
		peers.removeAll(ignorePeers);
		Collections.shuffle(peers);
		peers.stream()
			.limit(maxPeers)
			.forEach(peer -> {
				counters.add(CounterType.MEMPOOL_RELAYER_SENT_COUNT, txns.size());
				this.remoteEventDispatcher.dispatch(peer, mempoolAddMsg);
			});
	}
}
