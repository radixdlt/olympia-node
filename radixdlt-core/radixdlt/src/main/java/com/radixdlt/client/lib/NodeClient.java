/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.lib;

import org.json.JSONObject;

import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

import static java.util.Objects.requireNonNull;

public class NodeClient {
	private static final MediaType MEDIA_TYPE = MediaType.parse("application/json");

	private final String baseUrl;
	private final OkHttpClient client;

	private NodeClient(String baseUrl) {
		this.baseUrl = baseUrl;
		this.client = new OkHttpClient.Builder()
			.connectTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.pingInterval(30, TimeUnit.SECONDS)
			.build();

	}

	public static NodeClient create(String baseUrl) {
		requireNonNull(baseUrl);

		return new NodeClient(baseUrl);
	}

	public Result<List<TokenBalance>> callTokenBalances(RadixAddress address) {
		//TODO: finish
		return null;
	}

	public Result<JSONObject> call(String method, JSONObject params) {
		return performCall(wrap(method, params)).flatMap(this::parseJson);
	}

	private JSONObject wrap(String method, JSONObject params) {
		return jsonObject()
			.put("jsonrpc", "2.0")
			.put("method", method)
			.put("params", params);
	}

	private Result<JSONObject> parseJson(String text) {
		try {
			return Result.ok(new JSONObject(text));
		} catch ()
	}

	private Result<String> performCall(JSONObject json) {
		var body = RequestBody.create(MEDIA_TYPE, json.toString());
		var request = new Request.Builder().url(baseUrl + "/rpc").post(body).build();

		try (var response = client.newCall(request).execute(); var responseBody = response.body()) {
			return responseBody != null
				   ? Result.ok(responseBody.string())
				   : Result.fail("No content in response");
		} catch (IOException e) {
			return Result.fail(e);
		}
	}
}
