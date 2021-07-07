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

import java.util.List;
import java.util.Objects;

public final class AccountBalance {
	private final List<BalanceStakes> stakes;
	private final List<Balance> tokens;

	private AccountBalance(List<BalanceStakes> stakes, List<Balance> tokens) {
		this.stakes = stakes;
		this.tokens = tokens;
	}

	@JsonCreator
	public static AccountBalance create(
		@JsonProperty(value = "stakes", required = true) List<BalanceStakes> stakes,
		@JsonProperty(value = "tokens", required = true) List<Balance> tokens
	) {
		return new AccountBalance(stakes, tokens);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof AccountBalance)) {
			return false;
		}

		var that = (AccountBalance) o;
		return stakes.equals(that.stakes) && tokens.equals(that.tokens);
	}

	@Override
	public int hashCode() {
		return Objects.hash(stakes, tokens);
	}

	@Override
	public String toString() {
		return "{stakes:" + stakes + ", tokens:" + tokens + '}';
	}

	public List<BalanceStakes> getStakes() {
		return stakes;
	}

	public List<Balance> getTokens() {
		return tokens;
	}
}
