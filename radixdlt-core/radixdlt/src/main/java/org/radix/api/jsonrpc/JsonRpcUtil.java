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

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;

import java.util.List;
import java.util.Optional;

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
			return Optional.ofNullable(new JSONObject(data));
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

	public static JSONArray listToArray(Serialization serialization, final List<?> list) {
		var resultArray = jsonArray();
		list.stream()
			.map(o -> serialization.toJsonObject(o, DsonOutput.Output.API))
			.forEach(resultArray::put);
		return resultArray;
	}

	public static JSONObject methodNotFoundResponse(Object id) {
		return errorResponse(id, METHOD_NOT_FOUND, "Method not found");
	}

	public static JSONObject errorResponse(Object id, int code, String message, JSONObject data) {
		return commonFields(id).put("error", jsonObject().put("code", code).put("message", message).put("data", data));
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

	public static JSONObject response(Object id, JSONObject result) {
		return commonFields(id).put("result", result);
	}

	public static JSONObject simpleResponse(Object id, String key, boolean value) {
		return response(id, jsonObject().put(key, value));
	}

	public static JSONObject commonFields(final Object id) {
		return jsonObject().put("id", id).put("jsonrpc", "2.0");
	}
}
