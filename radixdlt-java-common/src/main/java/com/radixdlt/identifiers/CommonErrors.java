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

package com.radixdlt.identifiers;

import com.radixdlt.utils.functional.Failure;

public enum CommonErrors implements Failure {
	INVALID_VALIDATOR_ADDRESS(2509, "Invalid validator address {0}"),
	INVALID_ACCOUNT_ADDRESS(2510, "Invalid account address {0}"),
	AID_IS_NULL(1601, "AID string is 'null'"),
	INVALID_LENGTH(1602, "AID string has incorrect length {0}"),
	UNABLE_TO_DECODE(1603, "Unable to decode: {0}"),
	UNABLE_TO_DESERIALIZE(1604, "Unable to deserialize: {0}"),
	CANT_MAKE_RECOVERABLE(1701, "Unable to convert signature to recoverable {0}"),
	INVALID_RADIX_ADDRESS(1702, "Invalid RadixAddress {0}");

	private final int code;
	private final String message;

	CommonErrors(int code, String message) {
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
