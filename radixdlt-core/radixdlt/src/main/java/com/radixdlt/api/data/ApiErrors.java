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

package com.radixdlt.api.data;

import com.radixdlt.utils.functional.Failure;

public enum ApiErrors implements Failure {
	INVALID_HEX_STRING(2502, "The value {0} is not a correct hexadecimal string"),
	MISSING_PARAMETER(2503, "The parameter {0} is missing"),
	SYMBOL_DOES_NOT_MATCH(2504, "Symbol {0} does not match"),
	INVALID_PAGE_SIZE(2505, "Size {0} must be greater than zero"),
	MISSING_PARAMS(2506, "The 'params' field must be present and must be a JSON array"),
	INVALID_NETWORK_ID(2507, "Network ID is not an integer"),
	UNKNOWN_VALIDATOR(2508, "Validator {0} not found"),
	INVALID_BLOB(2511, "Invalid blob {0}"),
	INVALID_SIGNATURE_DER(2512, "Invalid signature DER {0}"),
	INVALID_PUBLIC_KEY(2513, "Invalid public key {0}"),
	INVALID_TX_ID(2514, "Invalid TX ID {0}"),
	UNABLE_TO_PREPARE_TX(2515, "Unable to prepare transaction {0}"),
	UNKNOWN_ACTION(2516, "Unknown action {0}"),
	UNSUPPORTED_ACTION(2517, "Action type {0} is not supported"),
	INVALID_ACTION_DATA(2518, "Action data are invalid {0}"),
	MISSING_FIELD(2519, "Field {0} is missing or invalid"),
	UNKNOWN_RRI(2520, "Unknown RRI {0}"),
	UNKNOWN_ACCOUNT_ADDRESS(2521, "Unknown account address {0}"),
	UNABLE_TO_RESTORE_CREATOR(2522, "Unable to restore creator from transaction {0}"),
	UNKNOWN_TX_ID(2523, "Transaction with id {0} not found");

	private final int code;
	private final String message;

	ApiErrors(int code, String message) {
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
