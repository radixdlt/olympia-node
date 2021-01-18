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

package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;

import java.util.Objects;

/**
 * Message to attempt to add a command to the mempool
 */
public final class MempoolAdd {
	private final Command command;

	private MempoolAdd(Command command) {
		this.command = command;
	}

	public Command getCommand() {
		return command;
	}

	public static MempoolAdd create(Command command) {
		Objects.requireNonNull(command);
		return new MempoolAdd(command);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(command);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MempoolAdd)) {
			return false;
		}

		MempoolAdd other = (MempoolAdd) o;
		return Objects.equals(this.command, other.command);
	}

	@Override
	public String toString() {
		return String.format("%s{cmd=%s}", this.getClass().getSimpleName(), this.command);
	}
}
