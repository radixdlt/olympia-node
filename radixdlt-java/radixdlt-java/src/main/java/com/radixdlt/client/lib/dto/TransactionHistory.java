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
import com.radixdlt.client.lib.api.NavigationCursor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public final class TransactionHistory {
	private final NavigationCursor cursor;
	private final List<TransactionDTO> transactions;

	private TransactionHistory(NavigationCursor cursor, List<TransactionDTO> transactions) {
		this.cursor = cursor;
		this.transactions = transactions;
	}

	@JsonCreator
	public static TransactionHistory create(
		@JsonProperty("cursor") NavigationCursor cursor,
		@JsonProperty(value = "transactions", required = true) List<TransactionDTO> transactions
	) {
		requireNonNull(cursor);
		requireNonNull(transactions);

		return new TransactionHistory(cursor, transactions);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TransactionHistory)) {
			return false;
		}

		var that = (TransactionHistory) o;
		return Objects.equals(cursor, that.cursor) && transactions.equals(that.transactions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(cursor, transactions);
	}

	@Override
	public String toString() {
		return "TransactionHistory(" + "cursor=" + cursor + ", transactions=" + transactions + ')';
	}

	public Optional<NavigationCursor> getCursor() {
		return Optional.ofNullable(cursor);
	}

	public List<TransactionDTO> getTransactions() {
		return transactions;
	}
}
