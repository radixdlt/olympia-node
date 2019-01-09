package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import java.util.Objects;

/**
 * A dispatchable action to request to close a websocket connection with a given node.
 */
public final class CloseWebSocketAction implements RadixNodeAction {
	private final RadixNode node;

	private CloseWebSocketAction(RadixNode node) {
		Objects.requireNonNull(node);
		this.node = node;
	}

	public static CloseWebSocketAction of(RadixNode node) {
		return new CloseWebSocketAction(node);
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public String toString() {
		return "CLOSE_WEBSOCKET_ACTION " + node;
	}
}
