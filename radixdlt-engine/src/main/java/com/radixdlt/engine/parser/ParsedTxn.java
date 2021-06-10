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

package com.radixdlt.engine.parser;

import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.Optional;

public final class ParsedTxn {
	private final List<REInstruction> instructions;
	private final ECPublicKey publicKey;
	private final Txn txn;
	private final byte[] msg;
	private final UInt256 feePaid;
	private final boolean disableResourceAllocAndDestroy;

	public ParsedTxn(
		Txn txn,
		UInt256 feePaid,
		List<REInstruction> instructions,
		byte[] msg,
		ECPublicKey publicKey,
		boolean disableResourceAllocAndDestroy
	) {
		this.txn = txn;
		this.feePaid = feePaid;
		this.instructions = instructions;
		this.msg = msg;
		this.publicKey = publicKey;
		this.disableResourceAllocAndDestroy = disableResourceAllocAndDestroy;
	}

	public Txn txn() {
		return txn;
	}

	public UInt256 getFeePaid() {
		return feePaid == null ? UInt256.ZERO : feePaid;
	}

	public List<REInstruction> instructions() {
		return instructions;
	}

	public Optional<byte[]> getMsg() {
		return Optional.ofNullable(msg);
	}

	public Optional<ECPublicKey> getSignedBy() {
		return Optional.ofNullable(publicKey);
	}

	public boolean disableResourceAllocAndDestroy() {
		return disableResourceAllocAndDestroy;
	}
}
