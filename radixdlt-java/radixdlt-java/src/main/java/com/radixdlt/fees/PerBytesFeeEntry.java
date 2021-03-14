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

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

/**
 * Fee entry for a per-byte fee.
 */
public final class PerBytesFeeEntry implements FeeEntry {
	private final long units;

	private final long threshold;

	private final UInt256 fee;

	private PerBytesFeeEntry(long units, long threshold, UInt256 fee) {
		if (units <= 0) {
			throw new IllegalArgumentException("Units must be positive: " + units);
		}
		if (threshold < 0) {
			throw new IllegalArgumentException("Threshold must be non-negative: " + threshold);
		}
		this.units = units;
		this.threshold = threshold;
		this.fee = Objects.requireNonNull(fee);
	}

	public static PerBytesFeeEntry of(long units, long threshold, UInt256 fee) {
		return new PerBytesFeeEntry(units, threshold, fee);
	}

	public long units() {
		return this.units;
	}

	public long threshold() {
		return this.threshold;
	}

	public UInt256 fee() {
		return this.fee;
	}

	@Override
	public UInt256 feeFor(Atom a, int feeSize, Set<Particle> outputs) {
		long numberOfUnits = feeSize / this.units;
		if (numberOfUnits <= this.threshold) {
			return UInt256.ZERO;
		}
		long overThresholdUnits = numberOfUnits - this.threshold;
		UInt384 totalFee = UInt384.from(this.fee).multiply(UInt256.from(overThresholdUnits));
		if (!totalFee.getHigh().isZero()) {
			throw new ArithmeticException("Fee overflow");
		}
		return totalFee.getLow();
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.threshold, this.units, this.fee);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof PerBytesFeeEntry)) {
			return false;
		}
		PerBytesFeeEntry that = (PerBytesFeeEntry) obj;
		return this.units == that.units
			&& this.threshold == that.threshold
			&& Objects.equals(this.fee, that.fee);
	}

	@Override
	public String toString() {
		return String.format(
			"%s[%s bytes per unit, >%s units, %s per unit]",
			getClass().getSimpleName(),
			this.units,
			this.threshold,
			this.fee
		);
	}
}
