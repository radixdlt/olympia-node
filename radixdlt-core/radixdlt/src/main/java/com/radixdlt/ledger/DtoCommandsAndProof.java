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

package com.radixdlt.ledger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Command;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * A commands and proof which has not been verified
 */
// TODO: Add signature and sender
@Immutable
@SerializerId2("ledger.commands_and_proof")
public final class DtoCommandsAndProof {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("commands")
	@DsonOutput(Output.ALL)
	private final ImmutableList<Command> commands;

	@JsonProperty("head")
	@DsonOutput(Output.ALL)
	private final DtoLedgerHeaderAndProof head;

	@JsonProperty("tail")
	@DsonOutput(Output.ALL)
	private final DtoLedgerHeaderAndProof tail;

	@JsonCreator
	public DtoCommandsAndProof(
		@JsonProperty("commands") ImmutableList<Command> commands,
		@JsonProperty("head") DtoLedgerHeaderAndProof head,
		@JsonProperty("tail") DtoLedgerHeaderAndProof tail
	) {
		this.commands = commands == null ? ImmutableList.of() : commands;
		this.head = Objects.requireNonNull(head);
		this.tail = Objects.requireNonNull(tail);
	}

	public ImmutableList<Command> getCommands() {
		return commands;
	}

	public DtoLedgerHeaderAndProof getHead() {
		return head;
	}

	public DtoLedgerHeaderAndProof getTail() {
		return tail;
	}

	@Override
	public String toString() {
		return String.format("%s{cmds=%s head=%s tail=%s}", this.getClass().getSimpleName(), commands, head, tail);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DtoCommandsAndProof that = (DtoCommandsAndProof) o;
		return Objects.equals(commands, that.commands)
				&& Objects.equals(head, that.head)
				&& Objects.equals(tail, that.tail);
	}

	@Override
	public int hashCode() {
		return Objects.hash(commands, head, tail);
	}
}
