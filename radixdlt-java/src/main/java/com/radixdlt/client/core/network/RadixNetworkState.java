package com.radixdlt.client.core.network;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Current state in time of a {@link RadixNetwork}
 */
public class RadixNetworkState {
	private final Map<RadixPeer, RadixPeerState> peers;

	public RadixNetworkState(Map<RadixPeer, RadixPeerState> peers) {
		Objects.requireNonNull(peers, "peers is required");

		// use identity map as peer is not immutable, but we want the convenience of one state per peer instance
		this.peers = Collections.unmodifiableMap(new IdentityHashMap<>(peers));
	}

	public Map<RadixPeer, RadixPeerState> getPeers() {
		return peers;
	}
}
