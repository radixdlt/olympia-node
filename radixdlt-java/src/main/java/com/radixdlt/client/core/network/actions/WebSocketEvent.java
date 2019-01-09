package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import java.util.Objects;

/**
 * A dispatchable event action signifying an event which has occurred to a websocket.
 */
public final class WebSocketEvent implements RadixNodeAction {
	public enum WebSocketEventType {
		CONNECTING,
		CONNECTED,
		CLOSING,
		DISCONNECTED,
		FAILED,
	}

	private final RadixNode node;
	private final WebSocketEventType type;

	public WebSocketEvent(WebSocketEventType type, RadixNode node) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(node);

		this.type = type;
		this.node = node;
	}

	public RadixNode getNode() {
		return node;
	}

	public WebSocketEventType getType() {
		return type;
	}

	public static WebSocketEvent nodeStatus(RadixNode node, WebSocketStatus status) {
		return new WebSocketEvent(WebSocketEventType.valueOf(status.name()), node);
	}

	public String toString() {
		return "WEBSOCKET_EVENT(" + type + ") " + node;
	}
}
