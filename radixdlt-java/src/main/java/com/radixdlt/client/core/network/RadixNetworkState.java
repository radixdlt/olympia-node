package com.radixdlt.client.core.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Current state in time of a {@link RadixNetwork}
 */
public class RadixNetworkState {
	private final Map<RadixPeer, RadixPeerState> peers;

	public RadixNetworkState(Map<RadixPeer, RadixPeerState> peers) {
		Objects.requireNonNull(peers, "peers is required");

		this.peers = Collections.unmodifiableMap(new LinkedHashMap<>(peers));
	}

	public Map<RadixPeer, RadixPeerState> getPeers() {
		return peers;
	}
}
