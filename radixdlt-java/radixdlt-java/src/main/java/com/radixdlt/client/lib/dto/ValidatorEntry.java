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
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

public final class ValidatorEntry {
	private final UInt256 stake;
	private final ValidatorAddress address;

	private ValidatorEntry(UInt256 stake, ValidatorAddress address) {
		this.stake = stake;
		this.address = address;
	}

	@JsonCreator
	public static ValidatorEntry create(
		@JsonProperty("stake") UInt256 stake,
		@JsonProperty("address") ValidatorAddress address
	) {
		return new ValidatorEntry(stake, address);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ValidatorEntry)) {
			return false;
		}

		var that = (ValidatorEntry) o;
		return stake.equals(that.stake) && address.equals(that.address);
	}

	@Override
	public int hashCode() {
		return Objects.hash(stake, address);
	}

	@Override
	public String toString() {
		return "{stake:" + stake + ", address:" + address + '}';
	}

	public UInt256 getStake() {
		return stake;
	}

	public ValidatorAddress getAddress() {
		return address;
	}
}
