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
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.ledger.DtoTxnsAndProof;
import org.radix.network.messaging.Message;

import java.util.Objects;

/**
 * Message with sync atoms as a response to sync request
 */
@SerializerId2("message.sync.sync_response")
public final class SyncResponseMessage extends Message {

	@JsonProperty("commands")
	@DsonOutput(Output.ALL)
	private final DtoTxnsAndProof commands;

	SyncResponseMessage() {
		// Serializer only
		this.commands = null;
	}

	public SyncResponseMessage(DtoTxnsAndProof commands) {
		this.commands = commands;
	}

	public DtoTxnsAndProof getCommands() {
		return commands;
	}

	@Override
	public String toString() {
		return String.format("%s{commands=%s}", getClass().getSimpleName(), commands);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SyncResponseMessage that = (SyncResponseMessage) o;
		return Objects.equals(commands, that.commands)
				&& Objects.equals(getTimestamp(), that.getTimestamp());
	}

	@Override
	public int hashCode() {
		return Objects.hash(commands, getTimestamp());
	}
}
