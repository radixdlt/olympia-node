package com.radixdlt.ledger;

import java.util.Objects;

/**
 * Observation of an {@link LedgerEntry} made in a {@link Ledger}
 */
public final class LedgerObservation {
	public enum Type {
		ADOPT,
		COMMIT
	}

	private final Type type;
	private final LedgerEntry entry;

	private LedgerObservation(Type type, LedgerEntry entry) {
		this.type = type;
		this.entry = entry;
	}

	public Type getType() {
		return type;
	}

	public LedgerEntry getEntry() {
		return entry;
	}

	public static LedgerObservation adopt(LedgerEntry entry) {
		Objects.requireNonNull(entry, "newAtom is required");
		return new LedgerObservation(Type.ADOPT, entry);
	}

	public static LedgerObservation commit(LedgerEntry entry) {
		Objects.requireNonNull(entry, "atom is required");
		return new LedgerObservation(Type.COMMIT, entry);
	}
}
