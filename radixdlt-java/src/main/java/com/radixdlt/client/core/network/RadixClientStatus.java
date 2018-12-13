package com.radixdlt.client.core.network;

public enum RadixClientStatus {
	WAITING, CONNECTING, OPEN, CLOSING, CLOSED, FAILURE;

	public boolean isActive() {
		return this == OPEN || this == CONNECTING;
	}
}
