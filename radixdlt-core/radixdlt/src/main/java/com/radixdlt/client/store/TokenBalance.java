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

package com.radixdlt.client.store;

import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.store.berkeley.BalanceEntry;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

import static java.util.Objects.requireNonNull;

@SerializerId2("radix.api.token.balance")
public class TokenBalance {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("rri")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RRI rri;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt256 amount;

	private TokenBalance(RRI rri, UInt256 amount) {
		this.rri = rri;
		this.amount = amount;
	}

	@JsonCreator
	public static TokenBalance create(RRI rri, UInt256 amount) {
		requireNonNull(rri);
		requireNonNull(amount);

		return new TokenBalance(rri, amount);
	}

	public static TokenBalance from(BalanceEntry balanceEntry) {
		return create(balanceEntry.getRri(), balanceEntry.getAmount());
	}

	public RRI getRri() {
		return rri;
	}

	public UInt256 getAmount() {
		return amount;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o instanceof TokenBalance) {
			var that = (TokenBalance) o;
			return rri.equals(that.rri) && amount.equals(that.amount);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(rri, amount);
	}

	@Override
	public String toString() {
		return rri + " = " + amount;
	}

	public JSONObject asJson() {
		return jsonObject().put("rri", rri.toString()).put("amount", amount.toString());
	}
}
