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

import java.util.List;
import java.util.Objects;

public class LocalValidatorInfo {
	private final ValidatorAddress address;
	private final UInt256 totalStake;
	private final String name;
	private final String url;
	private final boolean registered;
	private final List<DelegatedStake> stakes;

	public LocalValidatorInfo(
		ValidatorAddress address,
		UInt256 totalStake,
		String name,
		String url,
		boolean registered,
		List<DelegatedStake> stakes
	) {
		this.address = address;
		this.totalStake = totalStake;
		this.name = name;
		this.url = url;
		this.registered = registered;
		this.stakes = stakes;
	}

	@JsonCreator
	public static LocalValidatorInfo create(
		@JsonProperty("address") ValidatorAddress address,
		@JsonProperty("total_stake") UInt256 totalStake,
		@JsonProperty("name") String name,
		@JsonProperty("url") String url,
		@JsonProperty("registered") boolean registered,
		@JsonProperty("stakes") List<DelegatedStake> stakes
	) {
		return new LocalValidatorInfo(address, totalStake, name, url, registered, stakes);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof LocalValidatorInfo)) {
			return false;
		}

		var that = (LocalValidatorInfo) o;
		return registered == that.registered
			&& address.equals(that.address)
			&& totalStake.equals(that.totalStake)
			&& name.equals(that.name)
			&& url.equals(that.url)
			&& stakes.equals(that.stakes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, totalStake, name, url, registered, stakes);
	}

	@Override
	public String toString() {
		return "{address:" + address
			+ ", totalStake:" + totalStake
			+ ", name:'" + name + '\''
			+ ", url:'" + url + '\''
			+ ", registered:" + registered
			+ ", stakes:" + stakes + '}';
	}

	public ValidatorAddress getAddress() {
		return address;
	}

	public UInt256 getTotalStake() {
		return totalStake;
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}

	public boolean isRegistered() {
		return registered;
	}

	public List<DelegatedStake> getStakes() {
		return stakes;
	}
}
