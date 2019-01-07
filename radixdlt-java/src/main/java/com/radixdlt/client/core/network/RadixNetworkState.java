package com.radixdlt.client.core.network;

import com.radixdlt.client.core.network.epics.RadixNodesEpic;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Current state in time of a {@link RadixNodesEpic}
 */
public class RadixNetworkState {
	private final Map<RadixNode, RadixNodeStatus> peers;

	public RadixNetworkState(Map<RadixNode, RadixNodeStatus> peers) {
		Objects.requireNonNull(peers, "peers is required");

		this.peers = Collections.unmodifiableMap(peers);
	}

	public Map<RadixNode, RadixNodeStatus> getPeers() {
		return peers;
	}

	@Override
	public String toString() {
		return peers.toString();
	}
}
