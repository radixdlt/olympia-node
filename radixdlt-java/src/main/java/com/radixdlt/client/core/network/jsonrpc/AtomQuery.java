package com.radixdlt.client.core.network.jsonrpc;

import com.radixdlt.identifiers.RadixAddress;

import com.google.gson.JsonObject;

public class AtomQuery {
	private final RadixAddress address;

	public AtomQuery(RadixAddress address) {
		this.address = address;
	}

	public JsonObject toJson() {
		JsonObject query = new JsonObject();
		query.addProperty("address", address.toString());

		return query;
	}
}
