/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.mempool.messages;

import com.radixdlt.consensus.Command;
import java.util.Objects;

import org.radix.network.messaging.Message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("message.mempool.atomadded")
public final class MempoolAtomAddedMessage extends Message {
	@JsonProperty("command")
	@DsonOutput(Output.ALL)
	private final Command command;

	MempoolAtomAddedMessage() {
		// Serializer only
		super(0);
		this.command = null;
	}

	public MempoolAtomAddedMessage(int magic, Command command) {
		super(magic);
		this.command = Objects.requireNonNull(command);
	}

	public Command command() {
		return this.command;
	}

	@Override
	public String toString() {
		return String.format("%s{command=%s}", getClass().getSimpleName(), command);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		MempoolAtomAddedMessage that = (MempoolAtomAddedMessage) o;
		return Objects.equals(command, that.command)
				&& Objects.equals(getTimestamp(), that.getTimestamp())
				&& Objects.equals(getMagic(), that.getMagic());
	}

	@Override
	public int hashCode() {
		return Objects.hash(command, getTimestamp(), getMagic());
	}
}
