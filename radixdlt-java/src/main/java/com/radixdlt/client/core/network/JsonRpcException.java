package com.radixdlt.client.core.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Optional;

public class JsonRpcException extends Exception {
	private final JsonObject request;
	private final JsonObject error;

	public JsonRpcException(JsonObject request, JsonObject error) {
		super(Optional.ofNullable(error.getAsJsonObject("error"))
			.map(o -> o.getAsJsonPrimitive("message"))
			.map(JsonPrimitive::getAsString).orElse(error.toString()));

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
