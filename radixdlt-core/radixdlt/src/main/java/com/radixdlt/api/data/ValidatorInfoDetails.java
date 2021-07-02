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

import org.json.JSONObject;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.ValidatorDetails;
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
	private final boolean registered;
	private final int percentage;

	private ValidatorInfoDetails(
		ECPublicKey validator,
		REAddr owner,
		String name,
		String infoUrl,
		UInt256 totalStake,
		UInt256 ownerStake,
		boolean externalStakesAllowed,
		boolean registered,
		int percentage
	) {
		this.validator = validator;
		this.owner = owner;
		this.name = name;
		this.infoUrl = infoUrl;
		this.totalStake = totalStake;
		this.ownerStake = ownerStake;
		this.externalStakesAllowed = externalStakesAllowed;
		this.registered = registered;
		this.percentage = percentage;
	}

	public static ValidatorInfoDetails create(
		ECPublicKey validator,
		REAddr owner,
		String name,
		String infoUrl,
		UInt256 totalStake,
		UInt256 ownerStake,
		boolean externalStakesAllowed,
		boolean registered,
		int percentage
	) {
		requireNonNull(validator);
		requireNonNull(owner);
		requireNonNull(name);
		requireNonNull(totalStake);
		requireNonNull(ownerStake);

		return new ValidatorInfoDetails(
			validator, owner, name, infoUrl, totalStake, ownerStake, externalStakesAllowed, registered, percentage
		);
	}

	public static ValidatorInfoDetails create(ValidatorDetails details) {
		return create(
			details.getKey(),
			details.getOwner(),
			details.getName(),
			details.getUrl(),
			details.getStake(),
			details.getOwnerStake(),
			details.allowsDelegation(),
			details.registered(),
			details.getPercentage()
		);
	}

	public String getValidatorAddress(Addressing addressing) {
		return addressing.forValidators().of(validator);
	}

	public ECPublicKey getValidatorKey() {
		return validator;
	}

	public REAddr getOwner() {
		return owner;
	}

	public UInt256 getTotalStake() {
		return totalStake;
	}

	public ECPublicKey getValidator() {
		return validator;
	}

	public String getName() {
		return name;
	}

	public String getInfoUrl() {
		return infoUrl;
	}

	public UInt256 getOwnerStake() {
		return ownerStake;
	}

	public boolean isExternalStakesAllowed() {
		return externalStakesAllowed;
	}

	public boolean isRegistered() {
		return registered;
	}

	public int getPercentage() {
		return percentage;
	}

	public JSONObject asJson(Addressing addressing) {
		return jsonObject()
			.put("address", addressing.forValidators().of(validator))
			.put("ownerAddress", addressing.forAccounts().of(owner))
			.put("name", name)
			.put("infoURL", infoUrl)
			.put("totalDelegatedStake", totalStake)
			.put("ownerDelegation", ownerStake)
			.put("validatorFee", percentage)
			.put("registered", registered)
			.put("isExternalStakeAccepted", externalStakesAllowed);
	}
}
