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

package com.radixdlt.middleware2.network;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Command;
import java.util.Objects;

import org.radix.network.messaging.Message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("message.mempool.add")
public final class MempoolAddMessage extends Message {
	@JsonProperty("commands")
	@DsonOutput(Output.ALL)
	private final ImmutableList<Command> commands;

	MempoolAddMessage() {
		// Serializer only
		super(0);
		this.commands = null;
	}

	public MempoolAddMessage(int magic, ImmutableList<Command> commands) {
		super(magic);
		this.commands = Objects.requireNonNull(commands);
	}

	public ImmutableList<Command> commands() {
		return this.commands;
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
		MempoolAddMessage that = (MempoolAddMessage) o;
		return Objects.equals(commands, that.commands)
				&& Objects.equals(getTimestamp(), that.getTimestamp())
				&& Objects.equals(getMagic(), that.getMagic());
	}

	@Override
	public int hashCode() {
		return Objects.hash(commands, getTimestamp(), getMagic());
	}
}
