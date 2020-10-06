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

package com.radixdlt.consensus.bft;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Pair;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Vertex which has been executed in the prepare phase
 */
public final class PreparedVertex {
	private final VerifiedVertex vertex;

	private final LedgerHeader ledgerHeader;

	private final ImmutableSet<Command> successfulCommands;
	private final ImmutableMap<Command, Exception> commandExceptions;

	PreparedVertex(
		VerifiedVertex vertex,
		LedgerHeader ledgerHeader,
		ImmutableSet<Command> successfulCommands,
		ImmutableMap<Command, Exception> commandExceptions
	) {
		this.vertex = Objects.requireNonNull(vertex);
		this.ledgerHeader = Objects.requireNonNull(ledgerHeader);
		this.successfulCommands = Objects.requireNonNull(successfulCommands);
		this.commandExceptions = Objects.requireNonNull(commandExceptions);
	}

	public Hash getId() {
		return vertex.getId();
	}

	public Hash getParentId() {
		return vertex.getParentId();
	}

	public View getView() {
		return vertex.getView();
	}

	public Stream<Command> successfulCommands() {
		return successfulCommands.stream();
	}

	public Stream<Pair<Command, Exception>> errorCommands() {
		return commandExceptions.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue()));
	}

	public Stream<Command> getCommands() {
		return Stream.concat(successfulCommands(), errorCommands().map(Pair::getFirst));
	}

	/**
	 * Retrieve the resulting header which is to be persisted on ledger
	 * @return the header
	 */
	public LedgerHeader getLedgerHeader() {
		return ledgerHeader;
	}

	/**
	 * Retrieve the vertex which was executed
	 * @return the executed vertex
	 */
	public VerifiedVertex getVertex() {
		return vertex;
	}
}
