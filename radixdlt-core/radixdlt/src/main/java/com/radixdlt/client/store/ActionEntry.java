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

import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.client.AccountAddress;
import com.radixdlt.client.ValidatorAddress;
import com.radixdlt.crypto.ECPublicKey;
import org.json.JSONObject;

import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.client.api.ActionType;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

import static java.util.Objects.requireNonNull;

public class ActionEntry {
	private static final JSONObject JSON_TYPE_OTHER = jsonObject().put("type", "Other");

	private final ActionType type;

	private final String from;
	private final String to;
	private final UInt256 amount;
	private final REAddr rri;

	private ActionEntry(ActionType type, String from, String to, UInt256 amount, REAddr rri) {
		this.type = type;
		this.from = from;
		this.to = to;
		this.amount = amount;
		this.rri = rri;
	}

	private static ActionEntry create(ActionType type, String from, String to, UInt256 amount, REAddr rri) {
		requireNonNull(type);
		return new ActionEntry(type, from, to, amount, rri);
	}

	public static ActionEntry transfer(ECPublicKey user, TransferToken transferToken) {
		return create(
			ActionType.TRANSFER,
			AccountAddress.of(user),
			AccountAddress.of(transferToken.to()),
			transferToken.amount(),
			transferToken.rri()
		);
	}

	public static ActionEntry burn(ECPublicKey user, BurnToken burnToken) {
		return create(ActionType.BURN, AccountAddress.of(user), null, burnToken.amount(), burnToken.rri());
	}

	public static ActionEntry stake(ECPublicKey user, StakeTokens stakeToken, REAddr nativeToken) {
		return create(
			ActionType.STAKE,
			AccountAddress.of(user),
			ValidatorAddress.of(stakeToken.to()),
			stakeToken.amount(),
			nativeToken
		);
	}

	public static ActionEntry unstake(ECPublicKey user, UnstakeTokens unstakeToken, REAddr nativeToken) {
		return create(
			ActionType.UNSTAKE,
			ValidatorAddress.of(unstakeToken.from()),
			AccountAddress.of(user),
			unstakeToken.amount(),
			nativeToken
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

	public String getFrom() {
		return from;
	}

	public String getTo() {
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
				return json.put("to", to).put("rri", rri.toString());

			case UNSTAKE:
			case STAKE:
				return json.put("validator", to);

			default:
				return JSON_TYPE_OTHER;
		}
	}
}
