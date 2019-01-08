package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import java.util.Objects;

/**
 * A dispatchable action to request to start a websocket connection with a given node.
 */
public class ConnectWebSocketAction implements RadixNodeAction {
	private final RadixNode node;

	private ConnectWebSocketAction(RadixNode node) {
		Objects.requireNonNull(node);
		this.node = node;
	}

	public static ConnectWebSocketAction of(RadixNode node) {
		return new ConnectWebSocketAction(node);
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public String toString() {
		return "CONNECT_WEB_SOCKET_ACTION " + node;
	}
}
