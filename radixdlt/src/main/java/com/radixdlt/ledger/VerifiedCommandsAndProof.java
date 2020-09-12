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

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

/**
 * Commands along with proof that they have been committed on ledger
 */
public final class VerifiedCommandsAndProof {
	private final ImmutableList<Command> commands;
	private final VerifiedLedgerHeaderAndProof headerAndProof;

	public VerifiedCommandsAndProof(
		ImmutableList<Command> commands,
		VerifiedLedgerHeaderAndProof headerAndProof
	) {
		this.commands = commands == null ? ImmutableList.of() : commands;
		this.headerAndProof = Objects.requireNonNull(headerAndProof);
	}

	public long getFirstVersion() {
		if (commands.isEmpty()) {
			return headerAndProof.getStateVersion();
		}

		return headerAndProof.getStateVersion() - commands.size() + 1;
	}

	public ImmutableList<Command> getCommands() {
		return commands;
	}

	public void forEach(BiConsumer<Long, Command> consumer) {
		long firstVersion = getFirstVersion();
		for (int i = 0; i < commands.size(); i++) {
			consumer.accept(firstVersion + i, commands.get(i));
		}
	}

	public VerifiedCommandsAndProof truncateFromVersion(long version) {
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
		return new VerifiedCommandsAndProof(truncated, headerAndProof);
	}

	public int size() {
		return commands.size();
	}

	public boolean contains(Command command) {
		return commands.contains(command);
	}

	public VerifiedLedgerHeaderAndProof getHeader() {
		return headerAndProof;
	}

	@Override
	public int hashCode() {
		return Objects.hash(commands, headerAndProof);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VerifiedCommandsAndProof)) {
			return false;
		}

		VerifiedCommandsAndProof other = (VerifiedCommandsAndProof) o;
		return Objects.equals(this.commands, other.commands)
			&& Objects.equals(this.headerAndProof, other.headerAndProof);
	}

	@Override
	public String toString() {
		return String.format("%s{cmds=%s stateAndProof=%s}", this.getClass().getSimpleName(), commands, headerAndProof);
	}
}
