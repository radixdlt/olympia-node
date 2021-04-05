/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.mempool;

import com.radixdlt.atom.Txn;

import java.util.List;
import java.util.Objects;

/**
 * Message to attempt to add commands to the mempool
 */
public final class MempoolAdd {
	private final List<Txn> txns;

	private MempoolAdd(List<Txn> txns) {
		this.txns = txns;
	}

	public List<Txn> getTxns() {
		return txns;
	}

	public static MempoolAdd create(Txn txn) {
		Objects.requireNonNull(txn);
		return new MempoolAdd(List.of(txn));
	}

	public static MempoolAdd create(List<Txn> txns) {
		Objects.requireNonNull(txns);
		return new MempoolAdd(txns);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(txns);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MempoolAdd)) {
			return false;
		}

		MempoolAdd other = (MempoolAdd) o;
		return Objects.equals(this.txns, other.txns);
	}

	@Override
	public String toString() {
		return String.format("%s{txns=%s}", this.getClass().getSimpleName(), this.txns);
	}
}
