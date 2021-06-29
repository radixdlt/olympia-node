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

public class ValidatorDTO {
	private final String address;
	private final String ownerAddress;
	private final String name;
	private final String infoURL;
	private final UInt256 totalDelegatedStake;
	private final UInt256 ownerDelegation;
	private final long percentage;
	private final boolean isExternalStakeAccepted;
	private final boolean registered;

	private ValidatorDTO(
		String address,
		String ownerAddress,
		String name,
		String infoURL,
		UInt256 totalDelegatedStake,
		UInt256 ownerDelegation,
		long percentage,
		boolean isExternalStakeAccepted,
		boolean registered
	) {
		this.address = address;
		this.ownerAddress = ownerAddress;
		this.name = name;
		this.infoURL = infoURL;
		this.totalDelegatedStake = totalDelegatedStake;
		this.ownerDelegation = ownerDelegation;
		this.percentage = percentage;
		this.isExternalStakeAccepted = isExternalStakeAccepted;
		this.registered = registered;
	}

	@JsonCreator
	public static ValidatorDTO create(
		@JsonProperty(value = "address", required = true) String address,
		@JsonProperty(value = "ownerAddress", required = true) String ownerAddress,
		@JsonProperty(value = "name", required = true) String name,
		@JsonProperty(value = "infoURL", required = true) String infoURL,
		@JsonProperty(value = "totalDelegatedStake", required = true) UInt256 totalDelegatedStake,
		@JsonProperty(value = "ownerDelegation", required = true) UInt256 ownerDelegation,
		@JsonProperty(value = "rakePercentage", required = true) long percentage,
		@JsonProperty(value = "isExternalStakeAccepted", required = true) boolean isExternalStakeAccepted,
		@JsonProperty(value = "registered", required = true) boolean registered
	) {
		requireNonNull(address);
		requireNonNull(ownerAddress);
		requireNonNull(totalDelegatedStake);
		requireNonNull(ownerDelegation);

		return new ValidatorDTO(
			address, ownerAddress, name, infoURL, totalDelegatedStake,
			ownerDelegation, percentage, isExternalStakeAccepted, registered
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ValidatorDTO)) {
			return false;
		}

		var that = (ValidatorDTO) o;
		return isExternalStakeAccepted == that.isExternalStakeAccepted
			&& registered == that.registered
			&& address.equals(that.address)
			&& ownerAddress.equals(that.ownerAddress)
			&& name.equals(that.name)
			&& infoURL.equals(that.infoURL)
			&& totalDelegatedStake.equals(that.totalDelegatedStake)
			&& percentage == that.percentage
			&& ownerDelegation.equals(that.ownerDelegation);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			address, ownerAddress, name, infoURL, totalDelegatedStake,
			ownerDelegation, percentage, isExternalStakeAccepted, registered
		);
	}

	@Override
	public String toString() {
		return "ValidatorDTO("
			+ "address='" + address + '\''
			+ ", ownerAddress='" + ownerAddress + '\''
			+ ", name='" + name + '\''
			+ ", infoURL='" + infoURL + '\''
			+ ", totalDelegatedStake=" + totalDelegatedStake
			+ ", ownerDelegation=" + ownerDelegation
			+ ", percentage=" + percentage
			+ ", isExternalStakeAccepted=" + isExternalStakeAccepted
			+ ", registered=" + registered
			+ ')';
	}

	public String getAddress() {
		return address;
	}

	public String getOwnerAddress() {
		return ownerAddress;
	}

	public String getName() {
		return name;
	}

	public String getInfoURL() {
		return infoURL;
	}

	public UInt256 getTotalDelegatedStake() {
		return totalDelegatedStake;
	}

	public UInt256 getOwnerDelegation() {
		return ownerDelegation;
	}

	public boolean isExternalStakeAccepted() {
		return isExternalStakeAccepted;
	}

	public long getPercentage() {
		return percentage;
	}

	public boolean isRegistered() {
		return registered;
	}
}
