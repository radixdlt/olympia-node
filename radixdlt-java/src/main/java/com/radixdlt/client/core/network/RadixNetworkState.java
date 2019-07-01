package com.radixdlt.client.core.network;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Current state of nodes connected to
 */
public final class RadixNetworkState {
	private final ImmutableMap<RadixNode, RadixNodeState> nodes;

	public RadixNetworkState(Map<RadixNode, RadixNodeState> nodes) {
		Objects.requireNonNull(nodes, "nodes is required");

		this.nodes = ImmutableMap.copyOf(nodes);
	}

	public static RadixNetworkState of(RadixNode node, RadixNodeState state) {
		return new RadixNetworkState(ImmutableMap.of(node, state));
	}

	public Map<RadixNode, RadixNodeState> getNodeStates() {
		return nodes;
	}

	public Set<RadixNode> getNodes() {
		return nodes.keySet();
	}

	@Override
	public String toString() {
		return nodes.toString();
	}
}
