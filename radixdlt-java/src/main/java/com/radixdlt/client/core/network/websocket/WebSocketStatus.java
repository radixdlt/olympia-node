package com.radixdlt.client.core.network.websocket;

public enum WebSocketStatus {
	WAITING, CONNECTING, CONNECTED, CLOSING, DISCONNECTED, FAILED;

	public boolean isActive() {
		return this == CONNECTED || this == CONNECTING;
	}
}
