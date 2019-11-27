package com.radixdlt.ledger;

import com.google.common.collect.ImmutableSet;

import java.util.Objects;
import java.util.Set;

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
	private final Set<? extends LedgerEntry> supersededLedgerEntries;

	private LedgerObservation(Type type, LedgerEntry entry, Set<? extends LedgerEntry> supersededLedgerEntries) {
		this.type = type;
		this.entry = entry;
		this.supersededLedgerEntries = supersededLedgerEntries;
	}

	public Type getType() {
		return type;
	}

	public LedgerEntry getEntry() {
		return entry;
	}

	public boolean hasSupersededAtoms() {
		return !supersededLedgerEntries.isEmpty();
	}

	public Set<? extends LedgerEntry> getSupersededEntries() {
		return supersededLedgerEntries;
	}

	public static LedgerObservation adopt(LedgerEntry entry) {
		Objects.requireNonNull(entry, "newAtom is required");
		return new LedgerObservation(Type.ADOPT, entry, ImmutableSet.of());
	}

	public static LedgerObservation adopt(Set<? extends LedgerEntry> supersededLedgerEntries, LedgerEntry entry) {
		Objects.requireNonNull(supersededLedgerEntries, "supersededLedgerEntries is required");
		Objects.requireNonNull(entry, "newAtom is required");
		return new LedgerObservation(Type.ADOPT, entry, supersededLedgerEntries);
	}

	public static LedgerObservation commit(LedgerEntry entry) {
		Objects.requireNonNull(entry, "atom is required");
		return new LedgerObservation(Type.COMMIT, entry, ImmutableSet.of());
	}
}
