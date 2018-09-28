package com.radixdlt.client.core.network;

import com.google.gson.JsonObject;

public class JsonRpcException extends Exception {
	private final JsonObject request;
	private final JsonObject error;

	public JsonRpcException(JsonObject request, JsonObject error) {
		super(error.getAsJsonObject("error").get("message").getAsString());
		this.request = request;
		this.error = error;
	}

	public JsonObject getRequest() {
		return request;
	}

	public JsonObject getError() {
		return error;
	}
}
