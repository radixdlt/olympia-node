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

package com.radixdlt.client.lib.api.action;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.ActionType;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class StakeAction implements Action {
	private final ActionType type = ActionType.STAKE;
	private final AccountAddress from;
	private final ValidatorAddress validator;
	private final UInt256 amount;

	@JsonCreator
	public StakeAction(
		@JsonProperty(value = "from", required = true) AccountAddress from,
		@JsonProperty(value = "validator", required = true) ValidatorAddress validator,
		@JsonProperty(value = "amount", required = true) UInt256 amount
	) {
		this.from = from;
		this.validator = validator;
		this.amount = amount;
	}

	public String toJSON(int networkId) {
		return String.format("{\"from\":\"%s\",\"validator\":\"%s\",\"amount\":\"%s\",\"type\":\"StakeTokens\"}",
							 from.toString(networkId), validator.toString(networkId), amount
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof StakeAction)) {
			return false;
		}

		var that = (StakeAction) o;
		return type == that.type && from.equals(that.from) && validator.equals(that.validator) && amount.equals(that.amount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, from, validator, amount);
	}
}
