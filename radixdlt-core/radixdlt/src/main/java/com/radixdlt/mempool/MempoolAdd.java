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
import com.radixdlt.identifiers.AID;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Message to attempt to add commands to the mempool
 */
public final class MempoolAdd {
	private final List<Txn> txns;
	private final Consumer<AID> onSuccess;
	private final Consumer<String> onError;

	private MempoolAdd(List<Txn> txns, Consumer<AID> onSuccess, Consumer<String> onError) {
		this.txns = txns;
		this.onSuccess = onSuccess;
		this.onError = onError;
	}

	public void onSuccess(AID txnId) {
		onSuccess.accept(txnId);
	}

	public void onError(String error) {
		onError.accept(error);
	}

	public List<Txn> getTxns() {
		return txns;
	}

	public static MempoolAdd create(Txn txn, Consumer<AID> onSuccess, Consumer<String> onError) {
		Objects.requireNonNull(txn);
		return new MempoolAdd(List.of(txn), onSuccess, onError);
	}

	public static MempoolAdd create(Txn txn) {
		Objects.requireNonNull(txn);
		return new MempoolAdd(List.of(txn), aid -> { }, err -> { });
	}

	public static MempoolAdd create(List<Txn> txns) {
		Objects.requireNonNull(txns);
		return new MempoolAdd(txns, aid -> { }, err -> { });
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
