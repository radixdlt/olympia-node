/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.network;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

import java.util.Objects;

/**
 * A push message with the latest ledger status update.
 */
@SerializerId2("message.sync.ledger_status_update")
public final class LedgerStatusUpdateMessage extends Message {

	@JsonProperty("header")
	@DsonOutput(Output.ALL)
	private final VerifiedLedgerHeaderAndProof header;

	LedgerStatusUpdateMessage() {
		// Serializer only
		super(0);
		this.header = null;
	}

	public LedgerStatusUpdateMessage(int magic, VerifiedLedgerHeaderAndProof header) {
		super(magic);
		this.header = header;
	}

	public VerifiedLedgerHeaderAndProof getHeader() {
		return header;
	}

	@Override
	public String toString() {
		return String.format("%s{header=%s}", getClass().getSimpleName(), header);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		LedgerStatusUpdateMessage that = (LedgerStatusUpdateMessage) o;
		return Objects.equals(header, that.header)
			&& Objects.equals(getTimestamp(), that.getTimestamp())
			&& Objects.equals(getMagic(), that.getMagic());
	}

	@Override
	public int hashCode() {
		return Objects.hash(header, getTimestamp(), getMagic());
	}
}
