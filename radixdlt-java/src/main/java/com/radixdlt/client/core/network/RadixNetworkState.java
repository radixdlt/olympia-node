package com.radixdlt.client.core.network;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Current state of nodes connected to
 */
public class RadixNetworkState {
	private final Map<RadixNode, RadixNodeState> nodes;

	public RadixNetworkState(Map<RadixNode, RadixNodeState> nodes) {
		Objects.requireNonNull(nodes, "nodes is required");

		this.nodes = Collections.unmodifiableMap(nodes);
	}

	public static RadixNetworkState of(RadixNode node, RadixNodeState state) {
		return new RadixNetworkState(ImmutableMap.of(node, state));
	}

	public Map<RadixNode, RadixNodeState> getNodes() {
		return nodes;
	}

	@Override
	public String toString() {
		return nodes.toString();
	}
}
