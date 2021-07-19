/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.network.p2p;

import com.google.common.collect.ImmutableList;
import com.radixdlt.network.p2p.transport.PeerChannel;

import javax.inject.Inject;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

/**
 * A Peers view using PeersManager
 */
public final class PeerManagerPeersView implements PeersView {
	private final PeerManager peerManager;

	@Inject
	public PeerManagerPeersView(PeerManager peerManager) {
		this.peerManager = peerManager;
	}

	@Override
	public Stream<PeerInfo> peers() {
		final var grouppedByNodeId = this.peerManager.activeChannels()
			.stream()
			.collect(groupingBy(PeerChannel::getRemoteNodeId));

		return grouppedByNodeId.entrySet().stream()
			.map(e -> {
				final var channelsInfo = e.getValue().stream()
					.map(c -> PeerChannelInfo.create(c.getUri(), c.getRemoteSocketAddress(), c.isOutbound()))
					.collect(ImmutableList.toImmutableList());
				return PeerInfo.create(e.getKey(), channelsInfo);
			});
	}
}
