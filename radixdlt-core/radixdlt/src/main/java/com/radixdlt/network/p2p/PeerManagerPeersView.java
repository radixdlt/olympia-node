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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.stream.Stream;

/**
 * A Peers view using PeersManager
 */
@Singleton
public final class PeerManagerPeersView implements PeersView {
	private final PeerManager peerManager;

	@Inject
	public PeerManagerPeersView(PeerManager peerManager) {
		this.peerManager = peerManager;
	}

	@Override
	public Stream<PeerInfo> peers() {
		return this.peerManager.activePeers()
			.stream()
			.map(p -> PeerInfo.create(p.getRemoteNodeId(), p.getUri(), p.getRemoteSocketAddress()));
	}
}
