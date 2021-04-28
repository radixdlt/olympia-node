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
import org.json.JSONObject;

import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.radixdlt.client.api.ApiErrors.MISSING_PARAMS_FIELD;
import static com.radixdlt.utils.functional.Failure.failure;
import static com.radixdlt.utils.functional.Result.fromOptional;

import static java.util.Optional.ofNullable;

/**
 * A collection of utilities for the Json RPC API
 */
public final class JsonRpcUtil {
	public static final String ARRAY = "___array___";

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


	public static Result<JSONObject> jsonObject(String data) {
		return Result.wrap(() -> new JSONObject(data));
	}

	public static JSONObject jsonObject() {
		return new JSONObject();
	}

	public static JSONArray jsonArray() {
		return new JSONArray();
	}

	public static <T> JSONArray fromList(List<T> input, Function<T, JSONObject> mapper) {
		var array = jsonArray();
		input.forEach(element -> array.put(mapper.apply(element)));
		return array;
	}

	public static Result<Integer> safeInteger(JSONObject params, String name) {
		return Result.wrap(() -> params.getInt(name));
	}

	public static Optional<String> safeString(JSONObject request, int index) {
		return params(request).flatMap(params -> ofNullable(params.opt(index)).map(Object::toString));
	}

	public static JSONObject parseError(String message) {
		return protocolError(RpcError.PARSE_ERROR, message);
	}

	public static JSONObject methodNotFound(JSONObject request) {
		return extendedError(request, RpcError.METHOD_NOT_FOUND, "Method not found");
	}

	public static JSONObject invalidParamsError(JSONObject request, Failure failure) {
		return invalidParamsError(request, failure.message());
	}

	public static JSONObject invalidParamsError(JSONObject request, String message) {
		return extendedError(request, RpcError.INVALID_PARAMS, message);
	}

	public static JSONObject requestTooLongError(int length) {
		return protocolError(RpcError.REQUEST_TOO_LONG, String.format("Request is too big: %d", length));
	}

	public static JSONObject serverError(JSONObject request, Exception e) {
		return extendedError(request, RpcError.SERVER_ERROR, e.getMessage());
	}

	public static JSONObject protocolError(RpcError code, String message) {
		return errorResponse(JSONObject.NULL, code, message);
	}

	private static Optional<JSONArray> params(JSONObject request) {
		return Optional.ofNullable(request.optJSONArray("params"));
	}

	private static JSONObject extendedError(JSONObject request, RpcError error, String message) {
		var response = jsonObject().put("code", error).put("message", message);

		params(request).ifPresent(params -> response.put("data", params));

		return commonFields(request.get("id")).put("error", response);
	}

	private static JSONObject errorResponse(Object id, RpcError code, String message) {
		return commonFields(id).put("error", jsonObject().put("code", code.code()).put("message", message));
	}

	private static JSONObject commonFields(Object id) {
		return jsonObject().put("jsonrpc", "2.0").put("id", id);
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
		return withParameters(
			request,
			params -> params.length() == 1,
			params -> fn.apply(params, params.getString(0))
		);
	}

	public static JSONObject withRequiredArrayParameter(
		JSONObject request,
		BiFunction<JSONArray, JSONArray, Result<JSONObject>> fn
	) {
		return withParameters(
			request,
			params -> !params.isEmpty(),
			params -> fn.apply(params, params.getJSONArray(0))
		);
	}

	public static JSONObject withRequiredParameters(
		JSONObject request,
		List<String> required,
		List<String> optional,
		Function<JSONObject, Result<JSONObject>> fn
	) {
		return withParameters(
			request,
			params -> params.length() > required.size(),
			params -> fn.apply(toNamed(required, optional, params))
		);
	}

	private static JSONObject toNamed(List<String> required, List<String> optional, JSONArray params) {
		var o = new JSONObject();
		for (int i = 0; i < required.size(); i++) {
			o.put(required.get(i), params.get(i));
		}
		for (int j = 0; j < params.length() - required.size(); j++) {
			o.put(optional.get(j), params.get(required.size() + j));
		}
		return o;
	}

	private static JSONObject withParameters(
		JSONObject request,
		Predicate<JSONArray> predicate,
		Function<JSONArray, Result<JSONObject>> fn
	) {
		return retrieveParams(request)
			.map(JSONArray.class::cast)
			.filter(predicate, failure(RpcError.INVALID_PARAMS.code(), "Parameter not present"))
			.flatMap(fn)
			.fold(failure -> invalidParamsError(request, failure), response -> response(request, response));
	}

	private static Result<JSONArray> retrieveParams(JSONObject request) {
		return fromOptional(ofNullable(request.optJSONArray("params")), MISSING_PARAMS_FIELD);
	}
}
