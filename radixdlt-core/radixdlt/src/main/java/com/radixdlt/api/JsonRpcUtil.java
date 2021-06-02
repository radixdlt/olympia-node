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

import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.radixdlt.api.data.ApiErrors.INVALID_HEX_STRING;
import static com.radixdlt.api.data.ApiErrors.MISSING_PARAMETER;
import static com.radixdlt.api.data.ApiErrors.MISSING_PARAMS;
import static com.radixdlt.identifiers.CommonErrors.UNABLE_TO_DECODE;
import static com.radixdlt.utils.functional.Failure.failure;
import static com.radixdlt.utils.functional.Result.fail;
import static com.radixdlt.utils.functional.Result.fromOptional;
import static com.radixdlt.utils.functional.Result.ok;
import static com.radixdlt.utils.functional.Result.wrap;

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
		return wrap(UNABLE_TO_DECODE, () -> new JSONObject(data));
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
		return wrap(UNABLE_TO_DECODE, () -> params.getInt(name));
	}

	public static Result<JSONArray> safeArray(JSONObject params, String name) {
		return fromOptional(MISSING_PARAMETER.with(name), ofNullable(params.optJSONArray(name)));
	}

	public static Result<String> safeString(JSONObject params, String name) {
		return fromOptional(MISSING_PARAMETER.with(name), optString(params, name));
	}

	public static Optional<String> optString(JSONObject params, String name) {
		return ofNullable(params.opt(name))
			.filter(String.class::isInstance)
			.map(String.class::cast);
	}

	public static Result<JSONObject> safeObject(JSONObject params, String name) {
		return fromOptional(MISSING_PARAMETER.with(name), ofNullable(params.optJSONObject(name)));
	}

	public static Result<byte[]> safeBlob(JSONObject params, String name) {
		return safeString(params, name)
			.flatMap(param -> wrap(INVALID_HEX_STRING, () -> Bytes.fromHexString(param)));
	}

	public static JSONObject parseError(String message) {
		return protocolError(RpcError.PARSE_ERROR, message);
	}

	public static JSONObject methodNotFound(JSONObject request, String method) {
		return extendedError(request, RpcError.METHOD_NOT_FOUND.code(), "Method " + method + " not found");
	}

	public static JSONObject invalidParamsError(JSONObject request, String message) {
		return extendedError(request, RpcError.INVALID_PARAMS.code(), message);
	}

	public static JSONObject protocolError(RpcError code, String message) {
		return commonFields(JSONObject.NULL).put("error", jsonObject()
			.put("code", code.code())
			.put("message", message)
		);
	}

	public static JSONObject extendedError(JSONObject request, int code, String message) {
		var response = jsonObject().put("code", code).put("message", message);

		ofNullable(request.opt("params")).ifPresent(params -> response.put("data", params));

		var id = ofNullable(request.opt("id")).orElse(JSONObject.NULL);

		return commonFields(id).put("error", response);
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
		String name,
		Function<String, Result<JSONObject>> fn
	) {
		return withRequiredParameters(
			request,
			List.of(name),
			List.of(),
			params -> safeString(params, name).flatMap(fn)
		);
	}

	public static JSONObject withNoParameters(JSONObject request, Supplier<JSONObject> fn) {
		return withRequiredParameters(request, List.of(), List.of(), __ -> ok(fn.get()));
	}

	public static JSONObject withRequiredParameters(
		JSONObject request,
		List<String> required,
		Function<JSONObject, Result<JSONObject>> fn
	) {
		return withRequiredParameters(request, required, List.of(), fn);
	}

	public static JSONObject withRequiredParameters(
		JSONObject request,
		List<String> required,
		List<String> optional,
		Function<JSONObject, Result<JSONObject>> fn
	) {
		return params(request)
			.flatMap(params -> sanitizeParams(params, required, optional))
			.flatMap(fn)
			.fold(
				failure -> extendedError(request, failure.code(), failure.message()),
				response -> response(request, response)
			);
	}

	private static Result<JSONObject> sanitizeParams(Object params, List<String> required, List<String> optional) {
		if (params instanceof JSONObject) {
			return ok((JSONObject) params);
		}

		if (params instanceof JSONArray) {
			return ok(toNamed((JSONArray) params, required, optional));
		}

		return fail(failure(RpcError.INVALID_REQUEST.code(), "Unable to parse request 'params' field"));
	}

	private static JSONObject toNamed(JSONArray params, List<String> required, List<String> optional) {
		var newParams = new JSONObject();

		for (int i = 0; i < required.size(); i++) {
			newParams.put(required.get(i), params.get(i));
		}

		for (int j = 0; j < params.length() - required.size(); j++) {
			newParams.put(optional.get(j), params.get(required.size() + j));
		}

		return newParams;
	}

	private static Result<Object> params(JSONObject request) {
		return fromOptional(MISSING_PARAMS, ofNullable(request.opt("params")));
	}
}
