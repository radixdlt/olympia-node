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

package com.radixdlt.mempool;

import com.radixdlt.constraintmachine.RETxn;

import java.util.Objects;
import java.util.Optional;

/**
 * An atom with additional information stored in a mempool.
 */
public final class MempoolTxn {

	private final RETxn reTxn;
	private final long inserted;
	private final Optional<Long> lastRelayed;

	private MempoolTxn(RETxn reTxn, long inserted, Optional<Long> lastRelayed) {
		this.reTxn = Objects.requireNonNull(reTxn);
		this.inserted = Objects.requireNonNull(inserted);
		this.lastRelayed = Objects.requireNonNull(lastRelayed);
	}

	public static MempoolTxn create(RETxn reTxn, long inserted, Optional<Long> lastRelayed) {
		return new MempoolTxn(reTxn, inserted, lastRelayed);
	}

	public RETxn getRETxn() {
		return reTxn;
	}

	public long getInserted() {
		return inserted;
	}

	public Optional<Long> getLastRelayed() {
		return lastRelayed;
	}

	public MempoolTxn withLastRelayed(long lastRelayed) {
		return new MempoolTxn(reTxn, inserted, Optional.of(lastRelayed));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final var that = (MempoolTxn) o;
		return inserted == that.inserted
			&& Objects.equals(lastRelayed, that.lastRelayed)
			&& Objects.equals(reTxn, that.reTxn);
	}

	@Override
	public int hashCode() {
		return Objects.hash(reTxn, inserted, lastRelayed);
	}

	@Override
	public String toString() {
		return String.format("%s{txn=%s inserted=%s lastRelayed=%s}",
			getClass().getSimpleName(), this.reTxn, this.inserted, this.lastRelayed);
	}
}
