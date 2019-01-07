package com.radixdlt.client.core.network.reducers;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeStatus;
import com.radixdlt.client.core.network.epics.RadixNodesEpic;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Current state in time of a {@link RadixNodesEpic}
 */
public class RadixNetworkState {
	private final Map<RadixNode, RadixNodeState> peers;

	public RadixNetworkState(Map<RadixNode, RadixNodeState> peers) {
		Objects.requireNonNull(peers, "peers is required");

		this.peers = Collections.unmodifiableMap(peers);
	}

	public static RadixNetworkState of(RadixNode node, RadixNodeState state) {
		return new RadixNetworkState(ImmutableMap.of(node, state));
	}

	public Map<RadixNode, RadixNodeState> getPeers() {
		return peers;
	}

	@Override
	public String toString() {
		return peers.toString();
	}
}
