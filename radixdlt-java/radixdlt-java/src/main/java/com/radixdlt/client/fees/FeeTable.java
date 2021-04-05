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

package com.radixdlt.client.fees;

import java.util.Objects;

import com.google.common.collect.ImmutableList;
import com.radixdlt.utils.UInt256;

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
