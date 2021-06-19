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

package com.radixdlt.atom;

public enum SubstateTypeId {
	UNCLAIMED_READDR((byte) 0),
	SYSTEM((byte) 1),
	TOKEN_DEF((byte) 2),
	TOKENS((byte) 3),
	PREPARED_STAKE((byte) 4),
	VALIDATOR((byte) 5),
	UNIQUE((byte) 6),
	TOKENS_LOCKED((byte) 7),
	STAKE_V1((byte) 8),
	ROUND_DATA((byte) 9),
	EPOCH_DATA((byte) 10),
	STAKE_OWNERSHIP((byte) 11),
	VALIDATOR_EPOCH_DATA((byte) 12),
	PREPARED_UNSTAKE((byte) 13),
	EXITTING_STAKE((byte) 14),
	RAKE_COPY((byte) 15),
	PREPARED_RAKE_UPDATE((byte) 16),
	STAKE_V2((byte) 17),
	NULL_VALIDATOR_UPDATE((byte) 18),
	PREPARED_VALIDATOR_UPDATE((byte) 19),
	ALLOW_DELEGATION_FLAG((byte) 20);

	private final byte id;

	SubstateTypeId(byte id) {
		this.id = id;
	}

	public byte id() {
		return id;
	}
}
