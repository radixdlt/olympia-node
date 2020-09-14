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
public class DtoCommandsAndProof {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("commands")
	@DsonOutput(Output.ALL)
	private final ImmutableList<Command> commands;

	@JsonProperty("start")
	@DsonOutput(Output.ALL)
	private final DtoLedgerHeaderAndProof start;

	@JsonProperty("end")
	@DsonOutput(Output.ALL)
	private final DtoLedgerHeaderAndProof end;

	@JsonCreator
	public DtoCommandsAndProof(
		@JsonProperty("commands") ImmutableList<Command> commands,
		@JsonProperty("start") DtoLedgerHeaderAndProof start,
		@JsonProperty("end") DtoLedgerHeaderAndProof end
	) {
		this.commands = commands == null ? ImmutableList.of() : commands;
		this.start = Objects.requireNonNull(start);
		this.end = Objects.requireNonNull(end);
	}

	public ImmutableList<Command> getCommands() {
		return commands;
	}

	public DtoLedgerHeaderAndProof getStartHeader() {
		return start;
	}

	public DtoLedgerHeaderAndProof getEndHeader() {
		return end;
	}

	@Override
	public String toString() {
		return String.format("%s{cmds=%s root=%s next=%s}", this.getClass().getSimpleName(), commands, start, end);
	}
}
