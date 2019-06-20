package com.radixdlt.client.core.network.jsonrpc;

import com.google.gson.JsonObject;

/**
 * Exception on Atom Submission to a node
 */
public class SubmitAtomException extends RuntimeException {
	private final JsonObject data;

	public SubmitAtomException(JsonObject data) {
		this.data = data;
	}

	public JsonObject getData() {
		return data;
	}
}
