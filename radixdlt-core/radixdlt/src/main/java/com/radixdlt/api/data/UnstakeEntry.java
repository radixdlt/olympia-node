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
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class UnstakeEntry {
	private final UInt256 amount;
	private final REAddr address;
	private final ECPublicKey validator;
	private final int epochsUntil;
	private final AID withdrawTxID;

	private UnstakeEntry(
		REAddr address,
		ECPublicKey validator,
		UInt256 amount,
		int epochsUntil,
		AID withdrawTxID
	) {
		this.address = address;
		this.validator = validator;
		this.epochsUntil = epochsUntil;
		this.withdrawTxID = withdrawTxID;
		this.amount = amount;
	}

	public static UnstakeEntry create(REAddr address, ECPublicKey validator, UInt256 amount, int epochsUntil, AID withdrawTxID) {
		Objects.requireNonNull(address);
		Objects.requireNonNull(validator);
		Objects.requireNonNull(amount);
		Objects.requireNonNull(withdrawTxID);

		return new UnstakeEntry(address, validator, amount, epochsUntil, withdrawTxID);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof UnstakeEntry)) {
			return false;
		}

		var that = (UnstakeEntry) o;

		return epochsUntil == that.epochsUntil
			&& amount.equals(that.amount)
			&& address.equals(that.address)
			&& validator.equals(that.validator)
			&& withdrawTxID.equals(that.withdrawTxID);
	}

	@Override
	public int hashCode() {
		return Objects.hash(amount, address, validator, epochsUntil, withdrawTxID);
	}

	@Override
	public String toString() {
		return String.format(
			"UnstakeEntry(%s, %s, %s, %s, %s)",
			address, amount, validator, epochsUntil, withdrawTxID
		);
	}

	public JSONObject asJson() {
		return jsonObject()
			.put("validator", ValidatorAddress.of(validator))
			.put("amount", amount)
			.put("epochsUntil", epochsUntil)
			.put("withdrawTxID", withdrawTxID);
	}
}