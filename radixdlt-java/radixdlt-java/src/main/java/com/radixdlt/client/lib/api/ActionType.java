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

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ActionType {
	MSG("Message"),
	TRANSFER("TokenTransfer"),
	STAKE("StakeTokens"),
	UNSTAKE("UnstakeTokens"),
	BURN("BurnTokens"),
	MINT("MintTokens"),
	REGISTER_VALIDATOR("RegisterValidator"),
	UNREGISTER_VALIDATOR("UnregisterValidator"),
	UPDATE_VALIDATOR_METADATA("UpdateValidatorMetadata"),
	UPDATE_VALIDATOR_FEE("UpdateValidatorFee"),
	UPDATE_VALIDATOR_OWNER("UpdateValidatorOwnerAddress"),
	UPDATE_VALIDATOR_DELEGATION_FLAG("UpdateAllowDelegationFlag"),
	CREATE_FIXED("CreateFixedSupplyToken"),
	CREATE_MUTABLE("CreateMutableSupplyToken"),
	UNKNOWN("Other");

	private final String text;

	private static final Map<String, ActionType> TO_ACTION_TYPE = Arrays.stream(values())
		.collect(Collectors.toMap(ActionType::toJson, Function.identity()));

	ActionType(String text) {
		this.text = text;
	}

	@JsonValue
	public String toJson() {
		return text;
	}

	@JsonCreator
	public static ActionType create(String action) {
		var result = TO_ACTION_TYPE.get(action);

		if (result == null) {
			throw new IllegalArgumentException("Unable to parse ActionType from : " + action);
		}

		return result;
	}
}
