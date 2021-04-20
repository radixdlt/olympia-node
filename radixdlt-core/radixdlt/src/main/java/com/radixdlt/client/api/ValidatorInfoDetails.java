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

package com.radixdlt.client.api;

import org.json.JSONObject;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

import static java.util.Objects.requireNonNull;

public class ValidatorInfoDetails {
	private final RadixAddress address;
	private final RadixAddress owner;
	private final String name;
	private final String infoUrl;
	private final UInt256 totalStake;
	private final UInt256 ownerStake;
	private final boolean externalStakesAllowed;

	private ValidatorInfoDetails(
		RadixAddress address,
		RadixAddress owner,
		String name,
		String infoUrl,
		UInt256 totalStake,
		UInt256 ownerStake,
		boolean externalStakesAllowed
	) {
		this.address = address;
		this.owner = owner;
		this.name = name;
		this.infoUrl = infoUrl;
		this.totalStake = totalStake;
		this.ownerStake = ownerStake;
		this.externalStakesAllowed = externalStakesAllowed;
	}

	public static ValidatorInfoDetails create(
		RadixAddress address,
		RadixAddress owner,
		String name,
		String infoUrl,
		UInt256 totalStake,
		UInt256 ownerStake,
		boolean externalStakesAllowed
	) {
		requireNonNull(address);
		requireNonNull(owner);
		requireNonNull(name);
		requireNonNull(totalStake);
		requireNonNull(ownerStake);

		return new ValidatorInfoDetails(address, owner, name, infoUrl, totalStake, ownerStake, externalStakesAllowed);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public JSONObject asJson() {
		return jsonObject()
			.put("address", address)
			.put("ownerAddress", owner)
			.put("name", name)
			.put("infoURL", infoUrl)
			.put("totalDelegatedStake", totalStake)
			.put("ownerDelegation", ownerStake)
			.put("isExternalStakeAccepted", externalStakesAllowed);
	}
}
