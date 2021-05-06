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

package com.radixdlt.client.lib.api;

import com.radixdlt.utils.functional.Failure;

public enum ClientLibraryErrors implements Failure {
	BASE_URL_IS_MANDATORY(1001, "Base URL is mandatory"),
	UNKNOWN_ACTION(1002, "Unknown action {0}"),
	NO_CONTENT(1003, "No content in response"),
	UNABLE_TO_READ_RESPONSE_BODY(1008, "Error while retrieving response body {0}");

	private final int code;
	private final String message;

	ClientLibraryErrors(int code, String message) {
		this.code = code;
		this.message = message;
	}

	@Override
	public String message() {
		return message;
	}

	@Override
	public int code() {
		return code;
	}
}
