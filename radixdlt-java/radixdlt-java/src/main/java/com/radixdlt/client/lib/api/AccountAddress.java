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
import com.fasterxml.jackson.annotation.JsonValue;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;

import java.util.Objects;

public class AccountAddress {
	private final REAddr address;

	public AccountAddress(REAddr address) {
		this.address = address;
	}

	@JsonCreator
	public static AccountAddress create(String address) throws DeserializeException {
		return new AccountAddress(com.radixdlt.identifiers.AccountAddress.parse(address));
	}

	public static AccountAddress create(REAddr address) {
		return new AccountAddress(address);
	}

	public static AccountAddress create(ECPublicKey publicKey) {
		return create(REAddr.ofPubKeyAccount(publicKey));
	}

	public REAddr getAddress() {
		return address;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof AccountAddress)) {
			return false;
		}

		var that = (AccountAddress) o;
		return address.equals(that.address);
	}

	@Override
	public int hashCode() {
		return Objects.hash(address);
	}

	@JsonValue
	public String toAccountAddress() {
		return com.radixdlt.identifiers.AccountAddress.of(address);
	}

	@Override
	public String toString() {
		return com.radixdlt.identifiers.AccountAddress.of(address);
	}
}
