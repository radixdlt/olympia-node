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

import java.util.Objects;
import java.util.Optional;

public class JsonRpcResponse<T> {
	@JsonProperty(value = "jsonrpc", required = true)
	private final String version;
	@JsonProperty(value = "id", required = true)
	private final String id;
	@JsonProperty("result")
	private final T result;
	@JsonProperty("error")
	private final ErrorInfo error;

	public JsonRpcResponse(
		@JsonProperty("jsonrpc") String version,
		@JsonProperty("id") String id,
		@JsonProperty("result") T result,
		@JsonProperty("error") ErrorInfo error
	) {
		this.version = version;
		this.id = id;
		this.result = result;
		this.error = error;
	}

	public Optional<ErrorInfo> error() {
		return Optional.ofNullable(error);
	}

	public Optional<T> result() {
		return Optional.ofNullable(result);
	}

	public T rawResult() {
		return result;
	}

	public String getVersion() {
		return version;
	}

	public String getId() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof JsonRpcResponse)) {
			return false;
		}

		var that = (JsonRpcResponse<?>) o;
		return version.equals(that.version)
			&& id.equals(that.id)
			&& Objects.equals(result, that.result)
			&& Objects.equals(error, that.error);
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, id, result, error);
	}

	@Override
	public String toString() {
		return "JsonRpcResponse(" + version + ", " + id + ", " + result + ", " + error + ')';
	}
}
