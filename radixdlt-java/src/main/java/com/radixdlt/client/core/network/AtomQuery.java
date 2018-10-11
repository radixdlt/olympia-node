package com.radixdlt.client.core.network;

import org.radix.common.ID.EUID;

import com.google.gson.JsonObject;

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
