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

import com.radixdlt.consensus.LedgerHeader;
import java.util.Objects;

public final class ExecutedVertex {
	public enum CommandStatus {
		SUCCESS, IGNORED, FAILED
	}

	private final VerifiedVertex vertex;

	private final LedgerHeader ledgerHeader;

	private final CommandStatus commandStatus;

	ExecutedVertex(VerifiedVertex vertex, LedgerHeader ledgerHeader, CommandStatus commandStatus) {
		this.vertex = Objects.requireNonNull(vertex);
		this.ledgerHeader = Objects.requireNonNull(ledgerHeader);
		this.commandStatus = commandStatus;
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

	/**
	 * Retrieve the status of the command. Should NOT be persisted on ledger
	 * @return status of command in vertex
	 */
	public CommandStatus getCommandStatus() {
		return commandStatus;
	}
}
