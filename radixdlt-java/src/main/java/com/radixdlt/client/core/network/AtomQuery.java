package com.radixdlt.client.core.network;

import com.google.gson.JsonObject;
import com.radixdlt.client.core.address.EUID;

public class AtomQuery {
	private final EUID destination;

	public AtomQuery(EUID destination) {
		this.destination = destination;
	}

	public JsonObject toJson() {
		JsonObject query = new JsonObject();
		query.addProperty("destination", destination.toString());

		return query;
	}
}
