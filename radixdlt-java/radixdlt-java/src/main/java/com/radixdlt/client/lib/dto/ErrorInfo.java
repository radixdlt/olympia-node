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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.utils.functional.Failure;

import java.util.Objects;
import java.util.Optional;

public class ErrorInfo {
	private final int code;
	private final String message;
	private final Object data;

	private ErrorInfo(int code, String message, Object data) {
		this.code = code;
		this.message = message;
		this.data = data;
	}

	@JsonCreator
	public static ErrorInfo create(
		@JsonProperty("code") int code,
		@JsonProperty("message") String message,
		@JsonProperty("data") Object data
	) {
		return new ErrorInfo(code, message, data);
	}

	public int getCode() {
		return code;
	}

	public Optional<String> getMessage() {
		return Optional.ofNullable(message);
	}

	public Optional<Object> getData() {
		return Optional.ofNullable(data);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ErrorInfo)) {
			return false;
		}

		var errorInfo = (ErrorInfo) o;
		return code == errorInfo.code && Objects.equals(message, errorInfo.message);
	}

	@Override
	public int hashCode() {
		return Objects.hash(code, message);
	}

	@Override
	public String toString() {
		return "{" + code + ", '" + message + "'}";
	}

	public Failure toFailure() {
		var text = (message == null && data == null)
			   ? "<empty>"
			   : message == null
				 ? data.toString()
				 : message;

		return Failure.failure(code, text);
	}
}
