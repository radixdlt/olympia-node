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

	public static ActionEntry fromStake(StakedTokensParticle stake) {
		return create(
			ActionType.STAKE,
			stake.getAddress(),
			stake.getDelegateAddress(),
			stake.getAmount(), stake.getTokDefRef()
		);
	}

	public static ActionEntry fromUnstake(StakedTokensParticle unstake) {
		return create(
			ActionType.UNSTAKE,
			unstake.getAddress(),
			unstake.getDelegateAddress(),
			unstake.getAmount(),
			unstake.getTokDefRef()
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

	public JSONObject asJson() {
		//TODO: return different JSON depending on the type!!!
		var json = jsonObject()
			.put("type", type.toString())
			.put("from", from)
			.put("amount", amount);

		switch (type) {
			case TRANSFER:
				json.put("to", to).put("rri", rri);
				break;
			case UNSTAKE:
			case STAKE:
				json.put("validator", to);
				json.put("validator", to);
				break;

			case BURN:
			case MINT:
			case REGISTER_VALIDATOR:
			case UNREGISTER_VALIDATOR:
			case CREATE_FIXED:
			case CREATE_MUTABLE:
				return json.put("type", "Other");
		}

		return json;
	}
}
