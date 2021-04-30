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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class JsonRpcRequest {
	private static final String VERSION = "2.0";
	private static final String METHOD_PREFIX = "radix.";

	private final String version;
	private final String id;
	private final String method;
	private final List<Object> parameters = new ArrayList<>();

	private JsonRpcRequest(String version, String id, String method, List<Object> parameters) {
		this.version = version;
		this.id = id;
		this.method = METHOD_PREFIX + method;
		this.parameters.addAll(parameters);
	}

	public static JsonRpcRequest create(String method, Long id, Object ...parameters) {
		return new JsonRpcRequest(VERSION, id.toString(), method, List.of(parameters));
	}

	@JsonProperty("jsonrpc")
	public String getVersion() {
		return version;
	}

	@JsonProperty("id")
	public String getId() {
		return id;
	}

	@JsonProperty("params")
	public List<?> getParameters() {
		return parameters;
	}

	@JsonProperty("method")
	public String getMethod() {
		return method;
	}

	public JsonRpcRequest addParameters(Object ...params) {
		parameters.addAll(List.of(params));
		return this;
	}
}
