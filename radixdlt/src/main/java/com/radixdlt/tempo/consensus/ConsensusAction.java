package com.radixdlt.tempo.consensus;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.tempo.TempoAtom;

import java.util.Objects;
import java.util.Set;

public final class ConsensusAction {
	public enum Type {
		COMMIT,
		SWITCH_PREFERENCE
	}

	private final Type type;
	private final TempoAtom preference;
	private final Set<TempoAtom> oldPreferences;

	private ConsensusAction(Type type, TempoAtom preference, Set<TempoAtom> oldPreferences) {
		this.type = Objects.requireNonNull(type);
		this.preference = Objects.requireNonNull(preference);
		this.oldPreferences = Objects.requireNonNull(oldPreferences);
	}

	public Type getType() {
		return type;
	}

	public TempoAtom getPreference() {
		return preference;
	}

	public Set<TempoAtom> getOldPreferences() {
		return oldPreferences;
	}

	@Override
	public String toString() {
		return String.format("%s %s", type, preference);
	}

	public static ConsensusAction commit(TempoAtom preference) {
		return new ConsensusAction(Type.COMMIT, preference, ImmutableSet.of());
	}

	public static ConsensusAction changePreference(TempoAtom newPreference, Set<TempoAtom> oldPreferences) {
		return new ConsensusAction(Type.SWITCH_PREFERENCE, newPreference, oldPreferences);
	}
}
