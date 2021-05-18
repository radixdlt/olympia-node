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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.api.store.MessageEntry;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import static com.radixdlt.api.JsonRpcUtil.fromList;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

import static java.util.Objects.requireNonNull;

@SerializerId2("radix.api.history")
public class TxHistoryEntry {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("txId")
	@DsonOutput(DsonOutput.Output.ALL)
	private final AID txId;

	@JsonProperty("ts")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Instant timestamp;

	@JsonProperty("fee")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt256 fee;

	@JsonProperty("msg")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String message;

	@JsonProperty("actions")
	@DsonOutput(DsonOutput.Output.ALL)
	private final List<ActionEntry> actions;

	private TxHistoryEntry(AID txId, Instant timestamp, UInt256 fee, String message, List<ActionEntry> actions) {
		this.txId = txId;
		this.timestamp = timestamp;
		this.fee = fee;
		this.message = message;
		this.actions = actions;
	}

	@JsonCreator
	public static TxHistoryEntry create(
		@JsonProperty("txId") AID txId,
		@JsonProperty("ts") Instant date,
		@JsonProperty("fee") UInt256 fee,
		@JsonProperty("msg") String message,
		@JsonProperty("actions") List<ActionEntry> actions
	) {
		requireNonNull(txId);
		requireNonNull(date);
		requireNonNull(fee);
		requireNonNull(actions);
		return new TxHistoryEntry(txId, date, fee, message, actions);
	}

	public Instant timestamp() {
		return timestamp;
	}

	public AID getTxId() {
		return txId;
	}

	public UInt256 getFee() {
		return fee;
	}

	public String getMessage() {
		return message;
	}

	public List<ActionEntry> getActions() {
		return actions;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof TxHistoryEntry)) {
			return false;
		}

		var that = (TxHistoryEntry) o;
		return txId.equals(that.txId)
			&& timestamp.equals(that.timestamp)
			&& fee.equals(that.fee)
			&& Objects.equals(message, that.message)
			&& actions.equals(that.actions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(txId, timestamp, fee, message, actions);
	}

	public JSONObject asJson() {
		return jsonObject()
			.put("txID", txId)
			.put("sentAt", DateTimeFormatter.ISO_INSTANT.format(timestamp))
			.put("fee", fee)
			.put("actions", fromList(actions, ActionEntry::asJson))
			.putOpt("message", message);
	}

	@Override
	public String toString() {
		return asJson().toString(2);
	}
}
