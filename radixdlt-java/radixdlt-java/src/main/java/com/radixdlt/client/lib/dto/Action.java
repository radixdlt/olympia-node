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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.ActionType;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.utils.UInt256;

import java.util.Objects;
import java.util.Optional;

public class Action {
	@JsonProperty("type")
	private final ActionType type;

	@JsonProperty("from")
	private final AccountAddress from;

	@JsonProperty("to")
	private final AccountAddress to;

	@JsonProperty("validator")
	private final ValidatorAddress validator;

	@JsonProperty("amount")
	private final UInt256 amount;

	@JsonProperty("rri")
	private final String rri;

	private Action(
		ActionType type, AccountAddress from, AccountAddress to, ValidatorAddress validator, UInt256 amount, String rri
	) {
		this.type = type;
		this.from = from;
		this.to = to;
		this.validator = validator;
		this.amount = amount;
		this.rri = rri;
	}

	@JsonCreator
	public static Action create(
		@JsonProperty("type") ActionType type,
		@JsonProperty("from") AccountAddress from,
		@JsonProperty("to") AccountAddress to,
		@JsonProperty("validator") ValidatorAddress validator,
		@JsonProperty("amount") UInt256 amount,
		@JsonProperty("rri") String rri
	) {
		return new Action(type, from, to, validator, amount, rri);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Action)) {
			return false;
		}

		var actionDTO = (Action) o;
		return type == actionDTO.type
			&& Objects.equals(from, actionDTO.from)
			&& Objects.equals(to, actionDTO.to)
			&& Objects.equals(validator, actionDTO.validator)
			&& Objects.equals(amount, actionDTO.amount)
			&& Objects.equals(rri, actionDTO.rri);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, from, to, validator, amount, rri);
	}

	@Override
	public String toString() {
		return "Action("
			+ "type=" + type
			+ ", from=" + from
			+ ", to=" + to
			+ ", validator=" + validator
			+ ", amount=" + amount
			+ ", rri=" + rri
			+ ')';
	}

	@JsonIgnore
	public ActionType getType() {
		return type;
	}

	@JsonIgnore
	public Optional<AccountAddress> getFrom() {
		return Optional.ofNullable(from);
	}

	@JsonIgnore
	public Optional<AccountAddress> getTo() {
		return Optional.ofNullable(to);
	}

	@JsonIgnore
	public Optional<ValidatorAddress> getValidator() {
		return Optional.ofNullable(validator);
	}

	@JsonIgnore
	public Optional<UInt256> getAmount() {
		return Optional.ofNullable(amount);
	}

	@JsonIgnore
	public Optional<String> getRri() {
		return Optional.ofNullable(rri);
	}
}
