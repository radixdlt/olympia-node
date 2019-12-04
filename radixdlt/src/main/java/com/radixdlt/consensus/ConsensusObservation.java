package com.radixdlt.consensus;

import com.radixdlt.store.LedgerEntry;

import java.util.Objects;

/**
 * Observation of an {@link LedgerEntry} made in {@link Consensus}
 */
public final class ConsensusObservation {
	public enum Type {
		ADOPT,
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

	public static ConsensusObservation adopt(LedgerEntry entry) {
		Objects.requireNonNull(entry, "newAtom is required");
		return new ConsensusObservation(Type.ADOPT, entry);
	}

	public static ConsensusObservation commit(LedgerEntry entry) {
		Objects.requireNonNull(entry, "atom is required");
		return new ConsensusObservation(Type.COMMIT, entry);
	}
}
