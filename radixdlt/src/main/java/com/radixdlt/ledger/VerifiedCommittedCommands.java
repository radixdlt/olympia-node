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
import com.radixdlt.consensus.VerifiedCommittedHeader;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import javax.annotation.concurrent.Immutable;

/**
 * A command which has been committed on ledger
 */
@Immutable
@SerializerId2("ledger.verified_committed_commands")
public final class VerifiedCommittedCommands {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("commands")
	@DsonOutput(Output.ALL)
	private final ImmutableList<Command> commands;

	@JsonProperty("proof")
	@DsonOutput(Output.ALL)
	private final VerifiedCommittedHeader proof;

	@JsonCreator
	public VerifiedCommittedCommands(
		@JsonProperty("commands") ImmutableList<Command> commands,
		@JsonProperty("proof") VerifiedCommittedHeader proof
	) {
		this.commands = commands;
		this.proof = Objects.requireNonNull(proof);
	}

	public long getFirstVersion() {
		if (commands.isEmpty()) {
			return proof.getLedgerState().getStateVersion();
		}

		return proof.getLedgerState().getStateVersion() - commands.size() + 1;
	}

	public void forEach(BiConsumer<Long, Command> consumer) {
		long firstVersion = getFirstVersion();
		for (int i = 0; i < commands.size(); i++) {
			consumer.accept(firstVersion + i, commands.get(i));
		}
	}

	public VerifiedCommittedCommands truncateFromVersion(long version) {
		long firstVersion = getFirstVersion();

		if (version + 1 < firstVersion) {
			throw new IllegalArgumentException("firstVersion is " + firstVersion + " but want " + version);
		}

		if (version + 1 == firstVersion) {
			return this;
		}

		int startIndex = (int) (version + 1 - firstVersion);
		ImmutableList<Command> truncated = IntStream.range(startIndex, commands.size())
			.mapToObj(commands::get).collect(ImmutableList.toImmutableList());
		return new VerifiedCommittedCommands(truncated, proof);
	}

	public int size() {
		return commands.size();
	}

	public boolean contains(Command command) {
		return commands.contains(command);
	}

	public VerifiedCommittedHeader getProof() {
		return proof;
	}

	@Override
	public int hashCode() {
		return Objects.hash(commands, proof);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VerifiedCommittedCommands)) {
			return false;
		}

		VerifiedCommittedCommands other = (VerifiedCommittedCommands) o;
		return Objects.equals(this.commands, other.commands)
			&& Objects.equals(this.proof, other.proof);
	}

	@Override
	public String toString() {
		return String.format("%s{cmds=%s proof=%s}", this.getClass().getSimpleName(), commands, proof);
	}
}
