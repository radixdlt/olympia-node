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

import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.UInt256;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

import static java.util.Objects.requireNonNull;

public class TxHistoryEntry {
	private final AID txId;
	private final Instant date;
	private final UInt256 fee;
	private final MessageEntry message;
	private final List<ActionEntry> actions;

	private TxHistoryEntry(AID txId, Instant date, UInt256 fee, MessageEntry message, List<ActionEntry> actions) {
		this.txId = txId;
		this.date = date;
		this.fee = fee;
		this.message = message;
		this.actions = actions;
	}

	public static TxHistoryEntry create(AID txId, Instant date, UInt256 fee, MessageEntry message, List<ActionEntry> actions) {
		requireNonNull(txId);
		requireNonNull(date);
		requireNonNull(fee);
		requireNonNull(actions);
		return new TxHistoryEntry(txId, date, fee, message, actions);
	}

	public Instant timestamp() {
		return date;
	}

	public JSONObject asJson() {
		return jsonObject()
			.put("txId", txId)
			.put("date", DateTimeFormatter.ISO_INSTANT.format(date))
			.put("fee", fee)
			.put("message", message)
			.put("actions", actions);
	}

	@Override
	public String toString() {
		return asJson().toString(2);
	}
}
