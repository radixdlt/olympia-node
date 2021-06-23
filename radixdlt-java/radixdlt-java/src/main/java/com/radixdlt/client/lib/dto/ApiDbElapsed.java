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

import java.util.Objects;

public class ApiDbElapsed {
	private final ReadWrite balance;
	private final ReadWrite transaction;
	private final ReadWrite token;
	private final TimeDTO flush;

	private ApiDbElapsed(ReadWrite balance, ReadWrite transaction, ReadWrite token, TimeDTO flush) {
		this.balance = balance;
		this.transaction = transaction;
		this.token = token;
		this.flush = flush;
	}

	@JsonCreator
	public static ApiDbElapsed create(
		@JsonProperty(value = "balance", required = true) ReadWrite balance,
		@JsonProperty(value = "transaction", required = true) ReadWrite transaction,
		@JsonProperty(value = "token", required = true) ReadWrite token,
		@JsonProperty(value = "flush", required = true) TimeDTO flush
	) {
		return new ApiDbElapsed(balance, transaction, token, flush);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ApiDbElapsed)) {
			return false;
		}

		var that = (ApiDbElapsed) o;
		return balance.equals(that.balance) && transaction.equals(that.transaction) && token.equals(that.token) && flush.equals(that.flush);
	}

	@Override
	public int hashCode() {
		return Objects.hash(balance, transaction, token, flush);
	}

	@Override
	public String toString() {
		return "{balance:" + balance + ", transaction:" + transaction + ", token:" + token + ", flush:" + flush + '}';
	}

	public ReadWrite getBalance() {
		return balance;
	}

	public ReadWrite getTransaction() {
		return transaction;
	}

	public ReadWrite getToken() {
		return token;
	}

	public TimeDTO getFlush() {
		return flush;
	}
}
