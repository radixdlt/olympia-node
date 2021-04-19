/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.fees;

import java.util.Objects;
import java.util.Set;

import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.utils.UInt256;

/**
 * Fee entry for a per-atom fee.
 */
public final class PerAtomFeeEntry implements FeeEntry {
	private final UInt256 fee;

	private PerAtomFeeEntry(UInt256 fee) {
		this.fee = Objects.requireNonNull(fee);
	}

	public static PerAtomFeeEntry of(UInt256 fee) {
		return new PerAtomFeeEntry(fee);
	}

	@Override
	public UInt256 feeFor(Txn txn, int feeSize, Set<Particle> outputs) {
		return this.fee;
	}

	public UInt256 fee() {
		return this.fee;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.fee);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof PerAtomFeeEntry)) {
			return false;
		}
		PerAtomFeeEntry that = (PerAtomFeeEntry) obj;
		return Objects.equals(this.fee, that.fee);
	}

	@Override
	public String toString() {
		return String.format("%s:%s", getClass().getSimpleName(), this.fee);
	}
}
