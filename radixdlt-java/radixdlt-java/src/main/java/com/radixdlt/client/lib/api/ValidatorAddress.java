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

package com.radixdlt.client.lib.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.ValidatorAddresses;
import com.radixdlt.serialization.DeserializeException;

import java.util.Objects;

public class ValidatorAddress {
	private static final ValidatorAddresses validatorAddresses = new ValidatorAddresses("vb"); // This needs to come from somewhere else

	@JsonIgnore
	private final ECPublicKey address;

	@JsonIgnore
	private ValidatorAddress(ECPublicKey address) {
		this.address = address;
	}

	@JsonCreator
	public static ValidatorAddress create(String address) throws DeserializeException {
		var publicKey = validatorAddresses.parse(address);
		return new ValidatorAddress(publicKey);
	}

	public static ValidatorAddress of(ECPublicKey publicKey) {
		return new ValidatorAddress(publicKey);
	}

	public ECPublicKey getAddress() {
		return address;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ValidatorAddress)) {
			return false;
		}

		var that = (ValidatorAddress) o;
		return address.equals(that.address);
	}

	@Override
	public int hashCode() {
		return Objects.hash(address);
	}

	@JsonValue
	public String toValidatorAddress() {
		return validatorAddresses.of(address);
	}

	@Override
	public String toString() {
		return validatorAddresses.of(address);
	}
}
