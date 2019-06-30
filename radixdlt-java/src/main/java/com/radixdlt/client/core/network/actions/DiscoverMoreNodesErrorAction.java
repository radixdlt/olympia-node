package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;

/**
 * Action which represents an error which occurred when trying to discover more nodes
 */
public final class DiscoverMoreNodesErrorAction implements RadixNodeAction {
	private final Throwable reason;

	public DiscoverMoreNodesErrorAction(Throwable reason) {
		this.reason = reason;
	}

	public Throwable getReason() {
		return reason;
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
