package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable action request for the live peers of a node
 */
public class GetLivePeersRequestAction implements JsonRpcMethodAction {
	private final RadixNode node;

	private GetLivePeersRequestAction(RadixNode node) {
		Objects.requireNonNull(node);
		this.node = node;
	}

	public static GetLivePeersRequestAction of(RadixNode node) {
		return new GetLivePeersRequestAction(node);
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public String toString() {
		return "GET_LIVE_PEERS_REQUEST " + node;
	}
}
