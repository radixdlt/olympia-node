/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.radixdlt.utils.functional.Result.fromOptional;

import static java.util.Optional.ofNullable;

/**
 * A collection of utilities for the Json RPC API
 */
public final class JsonRpcUtil {
	public static final String ARRAY = "___array___";

	private static final Result<JSONObject> MISSING_PARAMETER = Result.fail("Parameter not present");

	/**
	 * The following found at: https://www.jsonrpc.org/specification
	 */
	public enum RpcError {
		INVALID_REQUEST(-32600),
		INVALID_PARAMS(-32602),
		METHOD_NOT_FOUND(-32601),
		REQUEST_TOO_LONG(-32001),
		SERVER_ERROR(-32000),
		PARSE_ERROR(-32700);

		private final int code;

		RpcError(int code) {
			this.code = code;
		}

		public int code() {
			return code;
		}
	}

	private JsonRpcUtil() {
		throw new IllegalStateException("Can't construct");
	}

	public static Optional<JSONArray> params(JSONObject request) {
		return Optional.ofNullable(request.optJSONArray("params"));
	}

	public static JSONObject parseError(String message) {
		return errorResponse(RpcError.PARSE_ERROR, message);
	}

	public static JSONObject invalidParamsError(String message) {
		return errorResponse(RpcError.INVALID_PARAMS, message);
	}

	public static JSONObject invalidParamsError(JSONObject request, Failure failure) {
		return errorResponse(request, RpcError.INVALID_PARAMS, failure.message());
	}

	public static Result<JSONObject> jsonObject(String data) {
		return Result.wrap(() -> new JSONObject(data));
	}

	public static JSONObject jsonObject() {
		return new JSONObject();
	}

	public static JSONArray jsonArray() {
		return new JSONArray();
	}

	public static Optional<JSONArray> jsonArray(String data) {
		try {
			return Optional.of(new JSONArray(data));
		} catch (JSONException e) {
			return Optional.empty();
		}
	}

	public static <T> JSONArray fromList(List<T> input, Function<T, JSONObject> mapper) {
		var array = jsonArray();
		input.forEach(element -> array.put(mapper.apply(element)));
		return array;
	}

	public static Optional<Integer> safeInteger(JSONObject params, String name) {
		try {
			return Optional.of(params.getInt(name));
		} catch (JSONException e) {
			return Optional.empty();
		}
	}

	public static Optional<String> safeString(JSONObject params, String name) {
		return ofNullable(params.opt(name)).map(Object::toString);
	}

	public static Optional<String> safeString(JSONObject request, int index) {
		return params(request).flatMap(params -> ofNullable(params.opt(index)).map(Object::toString));
	}

	public static JSONObject errorResponse(Object id, RpcError code, String message) {
		return commonFields(id).put("error", jsonObject().put("code", code.code()).put("message", message));
	}

	public static JSONObject errorResponse(JSONObject request, RpcError code, String message, JSONObject data) {
		return errorResponse(request, code, message).put("data", data);
	}

	public static JSONObject errorResponse(JSONObject request, RpcError code, String message) {
		return errorResponse(request.get("id"), code, message);
	}

	public static JSONObject errorResponse(RpcError code, String message) {
		return errorResponse(JSONObject.NULL, code, message);
	}

	public static JSONObject notification(String method, JSONObject params) {
		return jsonObject()
			.put("jsonrpc", "2.0")
			.put("method", method)
			.put("params", params);
	}

	private static JSONObject commonFields(Object id) {
		return jsonObject().put("id", id).put("jsonrpc", "2.0");
	}

	public static JSONObject response(JSONObject request, Object result) {
		//FIXME: replace hack with proper solution
		if (result instanceof JSONObject) {
			var array = ((JSONObject) result).optJSONArray(ARRAY);
			if (array != null) {
				return commonFields(request.get("id")).put("result", array);
			}
		}

		return commonFields(request.get("id")).put("result", result);
	}


	public static JSONObject withRequiredStringParameter(
		JSONObject request,
		BiFunction<JSONArray, String, Result<JSONObject>> fn
	) {
		return withParameters(request, params ->
			params.length() == 1
			? fn.apply(params, params.getString(0))
			: MISSING_PARAMETER);
	}

	public static JSONObject withRequiredArrayParameter(
		JSONObject request,
		BiFunction<JSONArray, JSONArray, Result<JSONObject>> fn
	) {
		return withParameters(request, params ->
			!params.isEmpty()
			? fn.apply(params, params.getJSONArray(0))
			: MISSING_PARAMETER);
	}

	public static JSONObject withRequiredParameters(
		JSONObject request,
		List<String> required,
		List<String> optional,
		Function<JSONObject, Result<JSONObject>> fn
	) {
		return withParameters(request, params -> {
			if (params.length() < required.size()) {
				return MISSING_PARAMETER;
			}
			var o = new JSONObject();
			for (int i = 0; i < required.size(); i++) {
				o.put(required.get(i), params.get(i));
			}
			for (int j = 0; j < params.length() - required.size(); j++) {
				o.put(optional.get(j), params.get(required.size() + j));
			}

			return fn.apply(o);
		});
	}

	private static JSONObject withParameters(JSONObject request, Function<JSONArray, Result<JSONObject>> fn) {
		return retrieveParams(request)
			.map(JSONArray.class::cast)
			.flatMap(fn)
			.fold(failure -> invalidParamsError(request, failure), response -> response(request, response));
	}

	private static Result<JSONArray> retrieveParams(JSONObject request) {
		return fromOptional(
			ofNullable(request.optJSONArray("params")),
			"The 'params' field must be present and must be a JSON array"
		);
	}
}
