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
	VIRTUAL_PARENT((byte) 0x0),
	UNCLAIMED_READDR((byte) 0x1),
	ROUND_DATA((byte) 0x2),
	EPOCH_DATA((byte) 0x3),
	TOKEN_RESOURCE((byte) 0x4),
	TOKEN_RESOURCE_METADATA((byte) 0x5),
	TOKENS((byte) 0x6),
	PREPARED_STAKE((byte) 0x7),
	STAKE_OWNERSHIP((byte) 0x8),
	PREPARED_UNSTAKE((byte) 0x9),
	EXITTING_STAKE((byte) 0xa),
	VALIDATOR_META_DATA((byte) 0xb),
	VALIDATOR_STAKE_DATA((byte) 0xc),
	VALIDATOR_BFT_DATA((byte) 0xd),
	VALIDATOR_ALLOW_DELEGATION_FLAG((byte) 0xe),
	VALIDATOR_REGISTERED_FLAG_COPY((byte) 0xf),
	VALIDATOR_RAKE_COPY((byte) 0x10),
	VALIDATOR_OWNER_COPY((byte) 0x11);

	private final byte id;

	SubstateTypeId(byte id) {
		this.id = id;
	}

	public byte id() {
		return id;
	}
}
