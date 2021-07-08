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
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.Objects;

public final class LocalValidatorInfo {
	private final ValidatorAddress address;
	private final UInt256 totalStake;
	private final String name;
	private final String url;
	private final boolean registered;
	private final List<DelegatedStake> stakes;
	private final AccountAddress owner;
	private final double validatorFee;
	private final boolean allowDelegation;

	public LocalValidatorInfo(
		ValidatorAddress address,
		UInt256 totalStake,
		String name,
		String url,
		boolean registered,
		List<DelegatedStake> stakes,
		AccountAddress owner,
		double validatorFee,
		boolean allowDelegation
	) {
		this.address = address;
		this.totalStake = totalStake;
		this.name = name;
		this.url = url;
		this.registered = registered;
		this.stakes = stakes;
		this.owner = owner;
		this.validatorFee = validatorFee;
		this.allowDelegation = allowDelegation;
	}

	@JsonCreator
	public static LocalValidatorInfo create(
		@JsonProperty(value = "address", required = true) ValidatorAddress address,
		@JsonProperty(value = "totalStake", required = true) UInt256 totalStake,
		@JsonProperty(value = "name", required = true) String name,
		@JsonProperty(value = "url", required = true) String url,
		@JsonProperty(value = "registered", required = true) boolean registered,
		@JsonProperty(value = "stakes", required = true) List<DelegatedStake> stakes,
		@JsonProperty(value = "owner", required = true) AccountAddress owner,
		@JsonProperty(value = "validatorFee", required = true) double validatorFee,
		@JsonProperty(value = "allowDelegation", required = true) boolean allowDelegation
	) {
		return new LocalValidatorInfo(address, totalStake, name, url, registered, stakes, owner, validatorFee, allowDelegation);
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
			&& Double.compare(that.validatorFee, validatorFee) == 0
			&& allowDelegation == that.allowDelegation
			&& address.equals(that.address)
			&& totalStake.equals(that.totalStake)
			&& name.equals(that.name)
			&& url.equals(that.url)
			&& stakes.equals(that.stakes)
			&& owner.equals(that.owner);
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, totalStake, name, url, registered, stakes, owner, validatorFee, allowDelegation);
	}

	@Override
	public String toString() {
		return "{"
			+ "address=" + address
			+ ", totalStake=" + totalStake
			+ ", name='" + name + '\''
			+ ", url='" + url + '\''
			+ ", registered=" + registered
			+ ", stakes=" + stakes
			+ ", owner=" + owner
			+ ", validatorFee=" + validatorFee
			+ ", allowDelegation=" + allowDelegation
			+ '}';
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

	public AccountAddress getOwner() {
		return owner;
	}

	public double getValidatorFee() {
		return validatorFee;
	}

	public boolean isAllowDelegation() {
		return allowDelegation;
	}
}
