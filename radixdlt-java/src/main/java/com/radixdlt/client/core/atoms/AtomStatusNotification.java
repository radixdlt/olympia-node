package com.radixdlt.client.core.atoms;

import com.google.gson.JsonObject;

public final class AtomStatusNotification {
	private final AtomStatus atomStatus;
	private final JsonObject data;

	public AtomStatusNotification(AtomStatus atomStatus, JsonObject data) {
		this.atomStatus = atomStatus;
		this.data = data;
	}

	public AtomStatus getAtomStatus() {
		return atomStatus;
	}

	public JsonObject getData() {
		return data;
	}

	@Override
	public String toString() {
		return atomStatus.toString();
	}
}
