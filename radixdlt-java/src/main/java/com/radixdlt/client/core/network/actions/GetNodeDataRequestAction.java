package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable action request for the node data of a given node
 */
public final class GetNodeDataRequestAction implements JsonRpcMethodAction {
	private final RadixNode node;

	private GetNodeDataRequestAction(RadixNode node) {
		Objects.requireNonNull(node);
		this.node = node;
	}

	public static GetNodeDataRequestAction of(RadixNode node) {
		return new GetNodeDataRequestAction(node);
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public String toString() {
		return "GET_NODE_DATA_REQUEST " + node;
	}
}
