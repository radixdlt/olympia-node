package com.radixdlt.tempo.consensus;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.ledger.LedgerEntry;

import java.util.Objects;
import java.util.Set;

public final class ConsensusAction {
	public enum Type {
		COMMIT,
		SWITCH_PREFERENCE
	}

	private final Type type;
	private final LedgerEntry preference;
	private final Set<LedgerEntry> oldPreferences;

	private ConsensusAction(Type type, LedgerEntry preference, Set<LedgerEntry> oldPreferences) {
		this.type = Objects.requireNonNull(type);
		this.preference = Objects.requireNonNull(preference);
		this.oldPreferences = Objects.requireNonNull(oldPreferences);
	}

	public Type getType() {
		return type;
	}

	public LedgerEntry getPreference() {
		return preference;
	}

	public Set<LedgerEntry> getOldPreferences() {
		return oldPreferences;
	}

	@Override
	public String toString() {
		return String.format("%s %s", type, preference);
	}

	public static ConsensusAction commit(LedgerEntry preference) {
		return new ConsensusAction(Type.COMMIT, preference, ImmutableSet.of());
	}

	public static ConsensusAction changePreference(LedgerEntry newPreference, Set<LedgerEntry> oldPreferences) {
		return new ConsensusAction(Type.SWITCH_PREFERENCE, newPreference, oldPreferences);
	}
}
