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
	MALFORMED_TX(290),
	INSUFFICIENT_FUNDS(291),
	NOT_PERMITTED(292),
	SUBMISSION_FAILURE(310),
	//--- not described in spec
	ADDRESS_IS_MISSING(1001),
	NOT_A_SYSTEM(1002),
	RRI_NOT_AVAILABLE(1003),
	ALREADY_A_VALIDATOR(1004),
	INVALID_STATE(1005),
	NO_SYSTEM_PARTICLE(1006),
	NOT_ENOUGH_STAKED(1007),
	NEXT_VIEW_LE_CURRENT(1008),
	ALREADY_UNREGISTERED(1009),
	INSUFFICIENT_FUNDS_FOR_FEE(1010);

	private final int code;

	TxErrorCode(int code) {
		this.code = code;
	}

	public int code() {
		return code;
	}
}
