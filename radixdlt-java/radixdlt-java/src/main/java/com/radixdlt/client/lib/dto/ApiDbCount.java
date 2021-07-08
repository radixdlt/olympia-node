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

public final class ApiDbCount {
	private final Count flush;
	private final Size queue;
	private final ReadWriteStats balance;
	private final ReadWriteStats transaction;
	private final ReadWriteStats token;

	private ApiDbCount(
		Count flush,
		Size queue,
		ReadWriteStats balance,
		ReadWriteStats transaction,
		ReadWriteStats token
	) {
		this.flush = flush;
		this.queue = queue;
		this.balance = balance;
		this.transaction = transaction;
		this.token = token;
	}

	@JsonCreator
	public static ApiDbCount create(
		@JsonProperty(value = "flush", required = true) Count flush,
		@JsonProperty(value = "queue", required = true) Size queue,
		@JsonProperty(value = "balance", required = true) ReadWriteStats balance,
		@JsonProperty(value = "transaction", required = true) ReadWriteStats transaction,
		@JsonProperty(value = "token", required = true) ReadWriteStats token
	) {
		return new ApiDbCount(flush, queue, balance, transaction, token);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ApiDbCount)) {
			return false;
		}

		var that = (ApiDbCount) o;
		return flush.equals(that.flush)
			&& queue.equals(that.queue)
			&& balance.equals(that.balance)
			&& transaction.equals(that.transaction)
			&& token.equals(that.token);
	}

	@Override
	public int hashCode() {
		return Objects.hash(flush, queue, balance, transaction, token);
	}

	@Override
	public String toString() {
		return "{flush:" + flush
			+ ", queue:" + queue
			+ ", balance:" + balance
			+ ", transaction:" + transaction
			+ ", token:" + token + '}';
	}

	public Count getFlush() {
		return flush;
	}

	public Size getQueue() {
		return queue;
	}

	public ReadWriteStats getBalance() {
		return balance;
	}

	public ReadWriteStats getTransaction() {
		return transaction;
	}

	public ReadWriteStats getToken() {
		return token;
	}
}
