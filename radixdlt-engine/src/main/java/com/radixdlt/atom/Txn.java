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

import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;

import java.util.Objects;

public final class Txn {
	private final byte[] payload;
	private final AID id;

	private Txn(byte[] payload) {
		this.payload = Objects.requireNonNull(payload);
		var firstHash = HashUtils.sha256(payload);
		var secondHash = HashUtils.sha256(firstHash.asBytes());
		this.id = AID.from(secondHash.asBytes());
	}

	public static Txn create(byte[] payload) {
		return new Txn(payload);
	}

	public AID getId() {
		return id;
	}

	public byte[] getPayload() {
		return payload;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Txn)) {
			return false;
		}

		Txn other = (Txn) o;
		return Objects.equals(this.id, other.id);
	}

	@Override
	public String toString() {
		return String.format("%s{id=%s}", this.getClass().getSimpleName(), this.id);
	}
}
