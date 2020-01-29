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

package com.radixdlt.consensus;

import com.radixdlt.store.LedgerEntry;

import java.util.Objects;

/**
 * Observation of an {@link LedgerEntry} made in {@link Consensus}
 */
public final class ConsensusObservation {
	public enum Type {
		COMMIT
	}

	private final Type type;
	private final LedgerEntry entry;

	private ConsensusObservation(Type type, LedgerEntry entry) {
		this.type = type;
		this.entry = entry;
	}

	public Type getType() {
		return type;
	}

	public LedgerEntry getEntry() {
		return entry;
	}

	public static ConsensusObservation commit(LedgerEntry entry) {
		Objects.requireNonNull(entry, "atom is required");
		return new ConsensusObservation(Type.COMMIT, entry);
	}
}
