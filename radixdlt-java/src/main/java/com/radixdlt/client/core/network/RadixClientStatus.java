package com.radixdlt.client.core.network;

public enum RadixClientStatus {
	WAITING, CONNECTING, CONNECTED, CLOSING, DISCONNECTED, FAILED;

	public boolean isActive() {
		return this == CONNECTED || this == CONNECTING;
	}
}
