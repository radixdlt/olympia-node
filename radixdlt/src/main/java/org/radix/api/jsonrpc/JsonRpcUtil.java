package org.radix.api.jsonrpc;

import org.json.JSONObject;

/**
 * A collection of utilities for the Json RPC API
 */
public final class JsonRpcUtil {

	/**
	 * The following found at: https://www.jsonrpc.org/specification
	 */
	public static final int INVALID_REQUEST_CODE = -32600;

	public static final int OVERSIZED_REQUEST = -32001;

	private JsonRpcUtil() {
		throw new IllegalStateException("Can't construct");
	}

	public static JSONObject methodNotFoundResponse(Object id) {
		return errorResponse(id, -32601, "Method not found");
	}

	public static JSONObject errorResponse(Object id, int code, String message, JSONObject data) {
		JSONObject error = new JSONObject();
		error.put("id", id);
		error.put("jsonrpc", "2.0");
		JSONObject errorData = new JSONObject();
		errorData.put("code", code);
		errorData.put("message", message);
		errorData.put("data", data);
		error.put("error", errorData);
		return error;
	}

	public static JSONObject errorResponse(Object id, int code, String message) {
		JSONObject error = new JSONObject();
		error.put("id", id);
		error.put("jsonrpc", "2.0");
		JSONObject errorData = new JSONObject();
		errorData.put("code", code);
		errorData.put("message", message);
		error.put("error", errorData);
		return error;
	}

	public static JSONObject notification(String method, JSONObject params) {
		JSONObject notification = new JSONObject();
		notification.put("jsonrpc", "2.0");
		notification.put("method", method);
		notification.put("params", params);
		return notification;
	}

	public static JSONObject response(Object id, JSONObject result) {
		JSONObject response = new JSONObject();
		response.put("id", id);
		response.put("jsonrpc", "2.0");
		response.put("result", result);
		return response;
	}

	public static JSONObject simpleResponse(Object id, String key, boolean value) {
		JSONObject resultJson = new JSONObject();
		resultJson.put(key, value);
		return response(id, resultJson);
	}

	public static JSONObject simpleResponse(Object id, String key, String value) {
		JSONObject resultJson = new JSONObject();
		resultJson.put(key, value);
		return response(id, resultJson);
	}
}
