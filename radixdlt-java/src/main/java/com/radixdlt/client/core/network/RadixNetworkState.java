package com.radixdlt.client.core.network;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Current state in time of a {@link RadixNetwork}
 */
public class RadixNetworkState {
	private final Map<RadixPeer, RadixClientStatus> peers;

	public RadixNetworkState(Map<RadixPeer, RadixClientStatus> peers) {
		Objects.requireNonNull(peers, "peers is required");

		this.peers = Collections.unmodifiableMap(peers);
	}

	public Map<RadixPeer, RadixClientStatus> getPeers() {
		return peers;
	}

	@Override
	public String toString() {
		return peers.toString();
	}
}
