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
 */

package com.radixdlt.mempool;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atom.Txn;
import java.util.Objects;

/**
 * Message indicating that a list of commands should be relayed to peers.
 */
public final class MempoolRelayCommands {
	private final ImmutableList<Txn> txns;

	private MempoolRelayCommands(ImmutableList<Txn> txns) {
		this.txns = txns;
	}

	public static MempoolRelayCommands create(ImmutableList<Txn> txns) {
		Objects.requireNonNull(txns);
		return new MempoolRelayCommands(txns);
	}

	public ImmutableList<Txn> getTxns() {
		return txns;
	}

	@Override
	public int hashCode() {
		return Objects.hash(txns);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MempoolRelayCommands)) {
			return false;
		}

		MempoolRelayCommands other = (MempoolRelayCommands) o;
		return Objects.equals(this.txns, other.txns);
	}

	@Override
	public String toString() {
		return String.format("%s{txns=%s}", this.getClass().getSimpleName(), this.txns);
	}
}
