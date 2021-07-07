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
import com.radixdlt.utils.UInt256;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public final class BalanceStakes {
	private final String delegate;
	private final UInt256 amount;

	private BalanceStakes(String delegate, UInt256 amount) {
		this.delegate = delegate;
		this.amount = amount;
	}

	@JsonCreator
	public static BalanceStakes create(
		@JsonProperty(value = "delegate", required = true) String validator,
		@JsonProperty(value = "amount", required = true) UInt256 amount
	) {
		requireNonNull(validator);
		requireNonNull(amount);

		return new BalanceStakes(validator, amount);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof BalanceStakes)) {
			return false;
		}

		var that = (BalanceStakes) o;
		return delegate.equals(that.delegate) && amount.equals(that.amount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(delegate, amount);
	}

	@Override
	public String toString() {
		return "StakePositionsDTO(" + "validator=" + delegate + ", amount=" + amount +	')';
	}

	public String getDelegate() {
		return delegate;
	}

	public UInt256 getAmount() {
		return amount;
	}
}
