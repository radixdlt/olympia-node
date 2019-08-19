package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.reactive.TempoState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConflictsState implements TempoState {
	private final Map<EUID, Conflict> conflicts;

	public ConflictsState(Map<EUID, Conflict> conflicts) {
		this.conflicts = conflicts;
	}

	public boolean isPending(EUID tag) {
		return conflicts.containsKey(tag);
	}

	public TempoAtom getAtom(EUID tag, AID aid) {
		return conflicts.get(tag).getAtom(aid);
	}

	public Set<AID> getAids(EUID tag) {
		Conflict conflict = conflicts.get(tag);
		if (conflict == null) {
			throw new TempoException("Conflict with tag '" + tag + "' does not exist");
		}
		return conflict.getAids();
	}

	public TempoAtom getCurrentAtom(EUID tag) {
		Conflict conflict = conflicts.get(tag);
		if (conflict == null) {
			throw new TempoException("Conflict with tag '" + tag + "' does not exist");
		}
		return conflict.getCurrentAtom();
	}

	public ConflictsState with(EUID tag, TempoAtom currentAtom, ImmutableSet<TempoAtom> allConflictingAtoms) {
		Map<EUID, Conflict> newConflicts = new HashMap<>(conflicts);
		Map<AID, TempoAtom> allAtomsByAid = allConflictingAtoms.stream()
			.collect(Collectors.toMap(Atom::getAID, a -> a));
		newConflicts.put(tag, new Conflict(tag, currentAtom, allAtomsByAid));
		return new ConflictsState(newConflicts);
	}

	public ConflictsState without(EUID tag) {
		Map<EUID, Conflict> newConflicts = new HashMap<>(conflicts);
		newConflicts.remove(tag);
		return new ConflictsState(newConflicts);
	}

	@Override
	public Object getDebugRepresentation() {
		return ImmutableMap.of(
			"conflicts", conflicts.values().stream()
				.map(conflict -> ImmutableMap.of(
					"tag", conflict.tag,
					"currentAtom", conflict.currentAtom.getAID(),
					"conflictingAtoms", conflict.conflictingAtoms.keySet()
				))
		);
	}

	public static ConflictsState empty() {
		return new ConflictsState(ImmutableMap.of());
	}

	public static final class Conflict {
		private final EUID tag;
		private final TempoAtom currentAtom;
		private final Map<AID, TempoAtom> conflictingAtoms;

		private Conflict(EUID tag, TempoAtom currentAtom, Map<AID, TempoAtom> conflictingAtoms) {
			this.tag = tag;
			this.currentAtom = currentAtom;
			this.conflictingAtoms = conflictingAtoms;
		}

		public TempoAtom getCurrentAtom() {
			return currentAtom;
		}

		public TempoAtom getAtom(AID aid) {
			return conflictingAtoms.get(aid);
		}

		public Set<AID> getAids() {
			return conflictingAtoms.keySet();
		}
	}
}
