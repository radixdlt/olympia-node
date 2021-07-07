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

import java.util.Objects;

public final class LocalAccount {
	private final AccountAddress address;
	private final AccountBalance balance;

	private LocalAccount(AccountAddress address, AccountBalance balance) {
		this.address = address;
		this.balance = balance;
	}

	@JsonCreator
	public static LocalAccount create(
		@JsonProperty(value = "address", required = true) AccountAddress address,
		@JsonProperty(value = "balance", required = true) AccountBalance balance
	) {
		return new LocalAccount(address, balance);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof LocalAccount)) {
			return false;
		}
		LocalAccount that = (LocalAccount) o;
		return address.equals(that.address) && balance.equals(that.balance);
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, balance);
	}

	@Override
	public String toString() {
		return "{address:" + address + ", balance:" + balance + '}';
	}

	public AccountAddress getAddress() {
		return address;
	}

	public AccountBalance getBalance() {
		return balance;
	}
}
