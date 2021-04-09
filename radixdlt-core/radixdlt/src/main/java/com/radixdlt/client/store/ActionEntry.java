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
import org.radix.api.jsonrpc.ActionType;

import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

import static java.util.Objects.requireNonNull;

public class ActionEntry {
	private static final JSONObject JSON_TYPE_OTHER = jsonObject().put("type", "Other");

	private final ActionType type;

	private final RadixAddress from;
	private final RadixAddress to;
	private final UInt256 amount;
	private final RRI rri;

	private ActionEntry(ActionType type, RadixAddress from, RadixAddress to, UInt256 amount, RRI rri) {
		this.type = type;
		this.from = from;
		this.to = to;
		this.amount = amount;
		this.rri = rri;
	}

	public static ActionEntry create(ActionType type, RadixAddress from, RadixAddress to, UInt256 amount, RRI rri) {
		requireNonNull(type);
		requireNonNull(from);
		requireNonNull(to);
		requireNonNull(amount);
		requireNonNull(rri);
		return new ActionEntry(type, from, to, amount, rri);
	}

	public static ActionEntry fromStake(StakedTokensParticle stake, RRI nativeToken) {
		return create(
			ActionType.STAKE,
			stake.getAddress(),
			stake.getDelegateAddress(),
			stake.getAmount(), nativeToken
		);
	}

	public static ActionEntry fromUnstake(StakedTokensParticle unstake, RRI nativeToken) {
		return create(
			ActionType.UNSTAKE,
			unstake.getAddress(),
			unstake.getDelegateAddress(),
			unstake.getAmount(),
			nativeToken
		);
	}

	public static ActionEntry transfer(TransferrableTokensParticle transfer, RadixAddress owner) {
		return create(
			ActionType.TRANSFER,
			owner,
			transfer.getAddress(),
			transfer.getAmount(),
			transfer.getTokDefRef()
		);
	}

	public static ActionEntry unknown() {
		return new ActionEntry(ActionType.UNKNOWN, null, null, null, null);
	}

	public ActionType getType() {
		return type;
	}

	public UInt256 getAmount() {
		return amount;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public String toString() {
		return asJson().toString(2);
	}

	public JSONObject asJson() {
		var json = jsonObject()
			.put("type", type.toString())
			.put("from", from)
			.put("amount", amount);

		switch (type) {
			case TRANSFER:
				return json.put("to", to).put("rri", rri);

			case UNSTAKE:
			case STAKE:
				return json.put("validator", to);

			default:
				return JSON_TYPE_OTHER;
		}
	}
}
