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

package com.radixdlt.atom.actions;

import com.radixdlt.utils.functional.Failure;

public enum ActionErrors implements Failure {
	SUBMISSION_FAILURE(1500, "Transaction submission failed: {0}"),

	MALFORMED_TRANSACTION(1300, "Transaction request is malformed"),

	INSUFFICIENT_FUNDS(1301, "Insufficient balance"),
	NOT_PERMITTED(1302, "Not permitted"),
	ADDRESS_IS_MISSING(1303, "Address is missing"),
	NOT_A_SYSTEM(1304, "Not a system"),
	RRI_NOT_AVAILABLE(1305, "RRI is not available"),
	ALREADY_A_VALIDATOR(1306, "Already a validator"),
	INVALID_STATE(1307, "Invalid state"),
	NO_SYSTEM_PARTICLE(1308, "No system particle"),
	NOT_ENOUGH_STAKED(1309, "Not enough stacked"),
	NEXT_VIEW_LE_CURRENT(1310, "Next view is less or equal to current"),
	ALREADY_UNREGISTERED(1311, "Already unregistered"),
	INSUFFICIENT_FUNDS_FOR_FEE(1312, "Insufficient funds for fee"),
	DIFFERENT_SOURCE_ADDRESSES(1313, "Source addresses for actions must be identical"),
	INVALID_ACTION(1314, "Invalid action"),
	INVALID_ACTION_TYPE(1315, "Invalid action type"),
	INVALID_RRI(1316, "Invalid RRI"),
	INVALID_ADDRESS(1317, "Invalid address"),
	INVALID_VALIDATOR_ADDRESS(1318, "Invalid validator address"),
	INVALID_AMOUNT(1319, "Invalid amount"),
	TRANSACTION_ADDRESS_DOES_NOT_MATCH(1320, "Provided txID does not match provided transaction"),
	EMPTY_TRANSACTIONS_NOT_SUPPORTED(1321, "Empty transactions are not supported");

	private final int code;
	private final String message;

	ActionErrors(int code, String message) {
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
