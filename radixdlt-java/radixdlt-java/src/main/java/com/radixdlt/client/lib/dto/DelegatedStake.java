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
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

public final class DelegatedStake {
	private final UInt256 amount;
	private final AccountAddress delegator;

	private DelegatedStake(UInt256 amount, AccountAddress delegator) {
		this.amount = amount;
		this.delegator = delegator;
	}

	@JsonCreator
	public static DelegatedStake create(
		@JsonProperty(value = "amount", required = true) UInt256 amount,
		@JsonProperty(value = "delegator", required = true) AccountAddress delegator
	) {
		return new DelegatedStake(amount, delegator);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof DelegatedStake)) {
			return false;
		}

		var that = (DelegatedStake) o;
		return amount.equals(that.amount) && delegator.equals(that.delegator);
	}

	@Override
	public int hashCode() {
		return Objects.hash(amount, delegator);
	}

	@Override
	public String toString() {
		return "{amount:" + amount + ", delegator=" + delegator + '}';
	}

	public UInt256 getAmount() {
		return amount;
	}

	public AccountAddress getDelegator() {
		return delegator;
	}
}
