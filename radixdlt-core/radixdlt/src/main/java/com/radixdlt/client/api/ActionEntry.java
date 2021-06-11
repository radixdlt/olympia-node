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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.identifiers.ValidatorAddress;

import org.json.JSONObject;

import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;

import java.util.function.Function;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;

import static java.util.Objects.requireNonNull;

@SerializerId2("radix.api.action")
public class ActionEntry {
	private static final JSONObject JSON_TYPE_OTHER = jsonObject().put("type", "Other");

	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("type")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ActionType type;

	@JsonProperty("from")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String from;

	@JsonProperty("to")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String to;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt256 amount;

	@JsonProperty("rri")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String rri;

	private ActionEntry(ActionType type, String from, String to, UInt256 amount, String rri) {
		this.type = type;
		this.from = from;
		this.to = to;
		this.amount = amount;
		this.rri = rri;
	}

	@JsonCreator
	public static ActionEntry create(
		@JsonProperty("type") ActionType type,
		@JsonProperty("from") String from,
		@JsonProperty("to") String to,
		@JsonProperty("amount") UInt256 amount,
		@JsonProperty("rri") String rri
	) {
		requireNonNull(type);

		return new ActionEntry(type, from, to, amount, rri);
	}

	public static ActionEntry transfer(TransferToken transferToken, Function<REAddr, String> addrToRri) {
		return create(
			ActionType.TRANSFER,
			AccountAddress.of(transferToken.from()),
			AccountAddress.of(transferToken.to()),
			transferToken.amount(),
			addrToRri.apply(transferToken.resourceAddr())
		);
	}

	public static ActionEntry burn(BurnToken burnToken, Function<REAddr, String> addrToRri) {
		return create(
			ActionType.BURN,
			AccountAddress.of(burnToken.from()),
			null,
			burnToken.amount(),
			addrToRri.apply(burnToken.resourceAddr())
		);
	}

	public static ActionEntry stake(StakeTokens stakeToken, Function<REAddr, String> addrToRri) {
		return create(
			ActionType.STAKE,
			AccountAddress.of(stakeToken.from()),
			ValidatorAddress.of(stakeToken.to()),
			stakeToken.amount(),
			addrToRri.apply(REAddr.ofNativeToken())
		);
	}

	public static ActionEntry unstake(UnstakeTokens unstakeToken, Function<REAddr, String> addrToRri) {
		return create(
			ActionType.UNSTAKE,
			AccountAddress.of(unstakeToken.accountAddr()),
			ValidatorAddress.of(unstakeToken.from()),
			unstakeToken.amount(),
			addrToRri.apply(REAddr.ofNativeToken())
		);
	}

	public static ActionEntry unknown() {
		return new ActionEntry(ActionType.UNKNOWN, null, null, null, null);
	}

	public String getRri() {
		return rri;
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
				return json.put("to", to).put("rri", rri);

			case UNSTAKE:
			case STAKE:
				return json.put("validator", to);

			default:
				return JSON_TYPE_OTHER;
		}
	}
}
