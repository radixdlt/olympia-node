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

package com.radixdlt.api.data;

import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import org.json.JSONObject;

import com.radixdlt.utils.UInt256;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;

import static java.util.Objects.requireNonNull;

public class ValidatorInfoDetails {
	private final ECPublicKey validator;
	private final REAddr owner;
	private final String name;
	private final String infoUrl;
	private final UInt256 totalStake;
	private final UInt256 ownerStake;
	private final boolean externalStakesAllowed;

	private ValidatorInfoDetails(
		ECPublicKey validator,
		REAddr owner,
		String name,
		String infoUrl,
		UInt256 totalStake,
		UInt256 ownerStake,
		boolean externalStakesAllowed
	) {
		this.validator = validator;
		this.owner = owner;
		this.name = name;
		this.infoUrl = infoUrl;
		this.totalStake = totalStake;
		this.ownerStake = ownerStake;
		this.externalStakesAllowed = externalStakesAllowed;
	}

	public static ValidatorInfoDetails create(
		ECPublicKey validator,
		REAddr owner,
		String name,
		String infoUrl,
		UInt256 totalStake,
		UInt256 ownerStake,
		boolean externalStakesAllowed
	) {
		requireNonNull(validator);
		requireNonNull(owner);
		requireNonNull(name);
		requireNonNull(totalStake);
		requireNonNull(ownerStake);

		return new ValidatorInfoDetails(validator, owner, name, infoUrl, totalStake, ownerStake, externalStakesAllowed);
	}

	public String getValidatorAddress() {
		return ValidatorAddress.of(validator);
	}

	public ECPublicKey getValidatorKey() {
		return validator;
	}

	public UInt256 getTotalStake() {
		return totalStake;
	}

	public JSONObject asJson() {
		return jsonObject()
			.put("address", getValidatorAddress())
			.put("ownerAddress", AccountAddress.of(owner))
			.put("name", name)
			.put("infoURL", infoUrl)
			.put("totalDelegatedStake", totalStake)
			.put("ownerDelegation", ownerStake)
			.put("isExternalStakeAccepted", externalStakesAllowed);
	}
}
