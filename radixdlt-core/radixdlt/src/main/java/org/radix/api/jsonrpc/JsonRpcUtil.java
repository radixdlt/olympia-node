/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.api.jsonrpc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A collection of utilities for the Json RPC API
 */
public final class JsonRpcUtil {
	/**
	 * The following found at: https://www.jsonrpc.org/specification
	 */
	public static final int INVALID_REQUEST = -32600;
	public static final int INVALID_PARAMS = -32602;
	public static final int METHOD_NOT_FOUND = -32601;
	public static final int REQUEST_TOO_LONG = -32001;
	public static final int SERVER_ERROR = -32000;
	public static final int PARSE_ERROR = -32700;

	private JsonRpcUtil() {
		throw new IllegalStateException("Can't construct");
	}

	public static Optional<JSONObject> jsonObject(String data) {
		try {
			return Optional.of(new JSONObject(data));
		} catch (JSONException e) {
			return Optional.empty();
		}
	}

	public static JSONObject jsonObject() {
		return new JSONObject();
	}

	public static JSONArray jsonArray() {
		return new JSONArray();
	}

	public static JSONObject methodNotFoundResponse(Object id) {
		return errorResponse(id, METHOD_NOT_FOUND, "Method not found");
	}

	public static JSONObject errorResponse(Object id, int code, String message, JSONObject data) {
		return errorResponse(id, code, message).put("data", data);
	}

	public static JSONObject errorResponse(Object id, int code, String message) {
		return commonFields(id).put("error", jsonObject().put("code", code).put("message", message));
	}

	public static JSONObject errorResponse(int code, String message) {
		return errorResponse(JSONObject.NULL, code, message);
	}

	public static JSONObject notification(String method, JSONObject params) {
		return jsonObject()
			.put("jsonrpc", "2.0")
			.put("method", method)
			.put("params", params);
	}

	public static JSONObject successResponse(Object id) {
		return commonFields(id).put("result", jsonObject().put("success", true));
	}

	private static JSONObject commonFields(final Object id) {
		return jsonObject().put("id", id).put("jsonrpc", "2.0");
	}

	public static JSONObject response(final JSONObject request, final Object result) {
		return commonFields(request.get("id")).put("result", result);
	}

	public static JSONObject withNamedParameter(
		final JSONObject request,
		final String name,
		final BiFunction<JSONObject, String, JSONObject> fn
	) {
		return withParameters(request, params -> {
			if (!params.has(name)) {
				return errorResponse(request.get("id"), SERVER_ERROR, "Field '" + name + "' not present in params");
			} else {
				return fn.apply(params, params.getString(name));
			}
		});
	}

	public static JSONObject withParameters(final JSONObject request, final Function<JSONObject, JSONObject> fn) {
		if (!request.has("params")) {
			return errorResponse(request.get("id"), SERVER_ERROR, "params field is required");
		}

		final Object paramsObject = request.get("params");

		if (!(paramsObject instanceof JSONObject)) {
			return errorResponse(request.get("id"), SERVER_ERROR, "params field must be a JSON object");
		}

		return fn.apply((JSONObject) paramsObject);
	}
}
