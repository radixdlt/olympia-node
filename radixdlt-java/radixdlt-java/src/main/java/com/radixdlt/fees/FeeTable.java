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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atom.Atom;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

/**
 * Schedule of fees.
 */
public final class FeeTable {
	private final UInt256 minimumFee;
	private final ImmutableList<FeeEntry> feeEntries;

	private FeeTable(UInt256 minimumFee, ImmutableList<FeeEntry> feeEntries) {
		this.minimumFee = Objects.requireNonNull(minimumFee);
		this.feeEntries = Objects.requireNonNull(feeEntries);
	}

	public static FeeTable from(UInt256 minimumFee, ImmutableList<FeeEntry> feeEntries) {
		return new FeeTable(minimumFee, feeEntries);
	}

	public UInt256 minimumFee() {
		return this.minimumFee;
	}

	public ImmutableList<FeeEntry> feeEntries() {
		return this.feeEntries;
	}

	public UInt256 feeFor(Atom atomWithoutFees) {
		final int atomSize = Serialize.getInstance().toDson(atomWithoutFees, DsonOutput.Output.HASH).length;
		final ImmutableSet<Particle> outputs = atomWithoutFees.spunParticles()
				.filter(sp -> Spin.UP.equals(sp.getSpin()))
				.map(SpunParticle::getParticle)
				.collect(ImmutableSet.toImmutableSet());
		return feeFor(atomWithoutFees, outputs, atomSize);
	}

	public UInt256 feeFor(Atom atomWithoutFees, Set<Particle> outputs, int atomSize) {
		UInt384 incrementalFees = UInt384.ZERO;
		for (FeeEntry entry : this.feeEntries) {
			incrementalFees = incrementalFees.add(entry.feeFor(atomWithoutFees, atomSize, outputs));
		}
		if (!incrementalFees.getHigh().isZero()) {
			throw new ArithmeticException("Fee overflow");
		}
		UInt256 incrementalFeeRequired = incrementalFees.getLow();
		return this.minimumFee.compareTo(incrementalFeeRequired) > 0 ? this.minimumFee : incrementalFeeRequired;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.minimumFee, this.feeEntries);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof FeeTable)) {
			return false;
		}
		FeeTable that = (FeeTable) o;
		return Objects.equals(this.minimumFee, that.minimumFee)
			&& Objects.equals(this.feeEntries, that.feeEntries);
	}

	@Override
	public String toString() {
		return String.format("%s[minmum=%s, %s]",
			getClass().getSimpleName(), this.minimumFee, this.feeEntries);
	}
}
