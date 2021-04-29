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

package com.radixdlt.client.handler;

import com.radixdlt.utils.functional.Failure;

public enum ClientLibErrors implements Failure {
	INVALID_NETWORK_ID(1001, "Network ID {0} is not an integer"),
	MISSING_NETWORK_ID(1002, "Network ID not found"),
	MISSING_FIELD(1003, "Field {0} is missing"),
	CONNECTION_ERROR(1004, "Error: {0}");

	private final int code;
	private final String message;

	ClientLibErrors(int code, String message) {
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
