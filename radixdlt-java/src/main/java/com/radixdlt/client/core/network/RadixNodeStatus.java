package com.radixdlt.client.core.network;

public enum RadixNodeStatus {
	WAITING, CONNECTING, CONNECTED, CLOSING, DISCONNECTED, FAILED;

	public boolean isActive() {
		return this == CONNECTED || this == CONNECTING;
	}
}
