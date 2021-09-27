/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.util;

import org.json.JSONArray;
import org.json.JSONObject;

import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Tuple.Tuple2;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.radixdlt.errors.ProtocolErrors.INVALID_REQUEST;
import static com.radixdlt.errors.ProtocolErrors.PARSE_ERROR;
import static com.radixdlt.errors.ApiErrors.MISSING_PARAMETER;
import static com.radixdlt.errors.ApiErrors.UNABLE_TO_PARSE_HEX_STRING;
import static com.radixdlt.errors.ApiErrors.UNABLE_TO_PARSE_INT;
import static com.radixdlt.utils.functional.Result.fail;
import static com.radixdlt.utils.functional.Result.fromOptional;
import static com.radixdlt.utils.functional.Result.ok;
import static com.radixdlt.utils.functional.Result.wrap;
import static com.radixdlt.utils.functional.Tuple.tuple;

import static java.util.Optional.ofNullable;

/**
 * A collection of utilities for the Json RPC API
 */
public final class JsonRpcUtil {
	private static final String ARRAY = "___array___";

	private JsonRpcUtil() {
		throw new IllegalStateException("Can't construct");
	}

	public static JSONObject jsonObject() {
		return new JSONObject();
	}

	public static JSONArray jsonArray() {
		return new JSONArray();
	}

	public static JSONObject wrapArray(JSONArray array) {
		return jsonObject().put(ARRAY, array);
	}

	public static <K, V> JSONArray fromMap(Map<K, V> input, BiFunction<K, V, Object> mapper) {
		var array = jsonArray();
		input.forEach((key, value) -> array.put(mapper.apply(key, value)));
		return array;
	}

	public static <T> JSONArray fromCollection(Collection<T> input, Function<T, Object> mapper) {
		var array = jsonArray();
		input.forEach(element -> array.put(mapper.apply(element)));
		return array;
	}

	public static Result<Integer> safeInteger(JSONObject params, String name) {
		return wrap(UNABLE_TO_PARSE_INT, () -> params.getInt(name));
	}

	public static Result<Long> safeLong(JSONObject params, String name) {
		return wrap(UNABLE_TO_PARSE_INT, () -> params.getLong(name));
	}

	public static Result<JSONArray> safeArray(JSONObject params, String name) {
		return fromOptional(() -> MISSING_PARAMETER.with(name), ofNullable(params.optJSONArray(name)));
	}

	public static Result<String> safeString(JSONObject params, String name) {
		return fromOptional(() -> MISSING_PARAMETER.with(name), optString(params, name));
	}

	public static Optional<String> optString(JSONObject params, String name) {
		return ofNullable(params.opt(name))
			.filter(String.class::isInstance)
			.map(String.class::cast);
	}

	public static Result<Tuple2<String, byte[]>> safeBlob(JSONObject params, String name) {
		return safeString(params, name)
			.flatMap(
				param -> wrap(
					() -> UNABLE_TO_PARSE_HEX_STRING.with(param),
					() -> tuple(param, Bytes.fromHexString(param))
				)
			);
	}

	public static JSONObject toResponse(JSONObject request, Result<JSONObject> result) {
		return result.fold(
			failure -> failureResponse(request, failure),
			response -> successResponse(request, response)
		);
	}

	public static JSONObject failureResponse(JSONObject request, Failure failure) {
		return extendedError(request, failure.code(), failure.message());
	}

	public static JSONObject successResponse(JSONObject request, JSONObject result) {
		var id = ofNullable(request.opt("id")).orElse(JSONObject.NULL);
		var value = ofNullable(result.opt(ARRAY)).orElse(result);

		return commonFields(id).put("result", value);
	}

	public static JSONObject withoutParameters(JSONObject request, Supplier<Result<JSONObject>> fn) {
		return withRequiredParameters(request, List.of(), __ -> fn.get());
	}

	public static JSONObject withRequiredStringParameter(
		JSONObject request,
		String name,
		Function<String, Result<JSONObject>> fn
	) {
		return withRequiredParameters(
			request,
			List.of(name),
			params -> safeString(params, name).flatMap(fn)
		);
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
		return toResponse(
			request,
			params(request)
				.flatMap(params -> sanitizeParams(params, required, optional))
				.flatMap(fn)
		);
	}

	private static JSONObject commonFields(Object id) {
		return jsonObject().put("jsonrpc", "2.0").put("id", id);
	}

	private static JSONObject extendedError(JSONObject request, int code, String message) {
		var response = jsonObject().put("code", code).put("message", message);

		ofNullable(request.opt("params")).ifPresent(params -> response.put("data", params));

		var id = ofNullable(request.opt("id")).orElse(JSONObject.NULL);

		return commonFields(id).put("error", response);
	}

	private static Result<JSONObject> sanitizeParams(Object params, List<String> required, List<String> optional) {
		if (params instanceof JSONObject) {
			return ok((JSONObject) params);
		}

		if (params instanceof JSONArray) {
			return ok(toNamed((JSONArray) params, required, optional));
		}

		return fail(INVALID_REQUEST.with("Unable to parse request 'params' field"));
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
		return fromOptional(PARSE_ERROR.with("The 'params' field is missing"), ofNullable(request.opt("params")));
	}
}
