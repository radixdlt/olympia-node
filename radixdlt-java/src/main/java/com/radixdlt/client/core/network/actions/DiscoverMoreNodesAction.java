package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;

/**
 * A dispatchable action which requests for more nodes to be discovered
 */
public final class DiscoverMoreNodesAction implements RadixNodeAction {
	private DiscoverMoreNodesAction() {
	}

	public static DiscoverMoreNodesAction instance() {
		return new DiscoverMoreNodesAction();
	}

	// TODO: remove this
	@Override
	public RadixNode getNode() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return "DISCOVER_MORE_NODES";
	}
}
