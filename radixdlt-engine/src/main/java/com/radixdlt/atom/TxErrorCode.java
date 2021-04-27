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

package com.radixdlt.atom;

public enum TxErrorCode {
	SUBMISSION_FAILURE(1500),
	MALFORMED_TX(1300),
	INSUFFICIENT_FUNDS(1301),
	NOT_PERMITTED(1302),
	ADDRESS_IS_MISSING(1303),
	NOT_A_SYSTEM(1304),
	RRI_NOT_AVAILABLE(1305),
	ALREADY_A_VALIDATOR(1306),
	INVALID_STATE(1307),
	NO_SYSTEM_PARTICLE(1308),
	NOT_ENOUGH_STAKED(1309),
	NEXT_VIEW_LE_CURRENT(1310),
	ALREADY_UNREGISTERED(1311),
	INSUFFICIENT_FUNDS_FOR_FEE(1312),
	DIFFERENT_SOURCE_ADDRESSES(1313),
	INVALID_ACTION(1314),
	INVALID_ACTION_TYPE(1315),
	INVALID_RRI(1316),
	INVALID_ADDRESS(1317),
	INVALID_VALIDATOR_ADDRESS(1318),
	INVALID_AMOUNT(1319);

	private final int code;

	TxErrorCode(int code) {
		this.code = code;
	}

	public int code() {
		return code;
	}
}
