package com.radixdlt.client.core.network;

import com.google.gson.JsonObject;

public class JsonRpcException extends Exception {
	public JsonRpcException(JsonObject request, JsonObject error) {
		super("Error: " + error.toString() + " on request: " + request.toString());
	}
}
