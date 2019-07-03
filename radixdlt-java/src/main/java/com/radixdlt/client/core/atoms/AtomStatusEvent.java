package com.radixdlt.client.core.atoms;

import com.google.gson.JsonObject;

/**
 * An event where an atom's status has changed.
 */
public final class AtomStatusEvent {
	private final AtomStatus atomStatus;
	private final JsonObject data;

	public AtomStatusEvent(AtomStatus atomStatus, JsonObject data) {
		this.atomStatus = atomStatus;
		this.data = data;
	}

	public AtomStatusEvent(AtomStatus atomStatus) {
		this(atomStatus, null);
	}

	public AtomStatus getAtomStatus() {
		return atomStatus;
	}

	public JsonObject getData() {
		return data;
	}

	@Override
	public String toString() {
		return atomStatus.toString() + " " + data;
	}
}
