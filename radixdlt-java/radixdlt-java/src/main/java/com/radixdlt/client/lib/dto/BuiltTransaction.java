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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class BuiltTransaction {
	private final TxBlob transaction;
	private final UInt256 fee;

	private BuiltTransaction(TxBlob transaction, UInt256 fee) {
		this.transaction = transaction;
		this.fee = fee;
	}

	@JsonCreator
	public static BuiltTransaction create(
		@JsonProperty(value = "transaction", required = true) TxBlob transaction,
		@JsonProperty(value = "fee", required = true) UInt256 fee
	) {
		requireNonNull(transaction);
		requireNonNull(fee);

		return new BuiltTransaction(transaction, fee);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof BuiltTransaction)) {
			return false;
		}

		var that = (BuiltTransaction) o;
		return transaction.equals(that.transaction) && fee.equals(that.fee);
	}

	@Override
	public int hashCode() {
		return Objects.hash(transaction, fee);
	}

	@Override
	public String toString() {
		return "BuiltTransactionDTO(transaction=" + transaction
			+ ", fee=" + fee + ')';
	}

	public TxBlob getTransaction() {
		return transaction;
	}

	public UInt256 getFee() {
		return fee;
	}

	public FinalizedTransaction toFinalized(ECKeyPair keyPair) {
		return FinalizedTransaction.create(this, keyPair);
	}
}
