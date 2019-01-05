package com.radixdlt.client.core.network;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Current state in time of a {@link RadixNetwork}
 */
public class RadixNetworkState {
	private final Map<RadixNode, RadixClientStatus> peers;

	public RadixNetworkState(Map<RadixNode, RadixClientStatus> peers) {
		Objects.requireNonNull(peers, "peers is required");

		this.peers = Collections.unmodifiableMap(peers);
	}

	public Map<RadixNode, RadixClientStatus> getPeers() {
		return peers;
	}

	@Override
	public String toString() {
		return peers.toString();
	}
}
