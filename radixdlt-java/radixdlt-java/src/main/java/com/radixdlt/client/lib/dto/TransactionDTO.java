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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.lib.api.TxTimestamp;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class TransactionDTO {
	private final AID txID;
	private final TxTimestamp sentAt;
	private final UInt256 fee;
	private final String message;
	private final List<Action> actions;

	private TransactionDTO(AID txID, TxTimestamp sentAt, UInt256 fee, String message, List<Action> actions) {
		this.txID = txID;
		this.sentAt = sentAt;
		this.fee = fee;
		this.message = message;
		this.actions = actions;
	}

	@JsonCreator
	public static TransactionDTO create(
		@JsonProperty("txID") AID txID,
		@JsonProperty("sentAt") TxTimestamp sentAt,
		@JsonProperty("fee") UInt256 fee,
		@JsonProperty("message") String message,
		@JsonProperty("actions") List<Action> actions
	) {
		requireNonNull(txID);
		requireNonNull(sentAt);
		requireNonNull(fee);
		requireNonNull(actions);

		return new TransactionDTO(txID, sentAt, fee, message, actions);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TransactionDTO)) {
			return false;
		}

		var that = (TransactionDTO) o;
		return txID.equals(that.txID)
			&& sentAt.equals(that.sentAt)
			&& fee.equals(that.fee)
			&& Objects.equals(message, that.message)
			&& actions.equals(that.actions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(txID, sentAt, fee, message, actions);
	}

	@Override
	public String toString() {
		return "Transaction("
			+ "txID=" + txID
			+ ", sentAt=" + sentAt
			+ ", fee=" + fee
			+ ", message='" + message + '\''
			+ ", actions=" + actions
			+ ')';
	}

	public AID getTxID() {
		return txID;
	}

	public TxTimestamp getSentAt() {
		return sentAt;
	}

	public UInt256 getFee() {
		return fee;
	}

	public Optional<String> getMessage() {
		return Optional.ofNullable(message);
	}

	public List<Action> getActions() {
		return actions;
	}
}
