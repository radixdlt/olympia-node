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

import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public final class TokenBalances {
	private final AccountAddress owner;
	private final List<Balance> tokenBalances;

	private TokenBalances(AccountAddress owner, List<Balance> tokenBalances) {
		this.owner = owner;
		this.tokenBalances = tokenBalances;
	}

	@JsonCreator
	public static TokenBalances create(
		@JsonProperty(value = "owner", required = true) AccountAddress owner,
		@JsonProperty(value = "tokenBalances", required = true) List<Balance> tokenBalances
	) {
		requireNonNull(owner);
		requireNonNull(tokenBalances);

		return new TokenBalances(owner, tokenBalances);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TokenBalances)) {
			return false;
		}

		var that = (TokenBalances) o;
		return owner.equals(that.owner) && tokenBalances.equals(that.tokenBalances);
	}

	@Override
	public int hashCode() {
		return Objects.hash(owner, tokenBalances);
	}

	@Override
	public String toString() {
		return "TokenBalancesDTO(" + "owner=" + owner + ", tokenBalances=" + tokenBalances + ')';
	}

	public AccountAddress getOwner() {
		return owner;
	}

	public List<Balance> getTokenBalances() {
		return tokenBalances;
	}
}
