package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ConflictsState implements TempoState {
	private final Map<EUID, Conflict> conflicts;

	public ConflictsState(Map<EUID, Conflict> conflicts) {
		this.conflicts = conflicts;
	}

	public TempoAtom getAtom(EUID tag, AID aid) {
		return conflicts.get(tag).getAtom(aid);
	}

	public Set<AID> getAids(EUID tag) {
		return conflicts.get(tag).getAids();
	}

	public TempoAtom getCurrentAtom(EUID tag) {
		return conflicts.get(tag).getCurrentAtom();
	}

	public ConflictsState with(EUID tag, TempoAtom currentAtom, ImmutableSet<TempoAtom> allConflictingAtoms, CompletableFuture<TempoAtom> winnerFuture) {
		Map<EUID, Conflict> newConflicts = new HashMap<>(conflicts);
		Map<AID, TempoAtom> allAtomsByAid = allConflictingAtoms.stream()
			.collect(Collectors.toMap(Atom::getAID, a -> a));
		newConflicts.put(tag, new Conflict(tag, currentAtom, allAtomsByAid, winnerFuture));
		return new ConflictsState(newConflicts);
	}

	public ConflictsState without(EUID tag) {
		Map<EUID, Conflict> newConflicts = new HashMap<>(conflicts);
		newConflicts.remove(tag);
		return new ConflictsState(newConflicts);
	}

	public static ConflictsState empty() {
		return new ConflictsState(ImmutableMap.of());
	}

	public void complete(EUID tag, TempoAtom winningAtom) {
		conflicts.get(tag).winnerFuture.complete(winningAtom);
	}

	public static final class Conflict {
		private final EUID tag;
		private final TempoAtom currentAtom;
		private final Map<AID, TempoAtom> conflictingAtoms;
		private final CompletableFuture<TempoAtom> winnerFuture;

		private Conflict(EUID tag, TempoAtom currentAtom, Map<AID, TempoAtom> conflictingAtoms, CompletableFuture<TempoAtom> winnerFuture) {
			this.tag = tag;
			this.currentAtom = currentAtom;
			this.conflictingAtoms = conflictingAtoms;
			this.winnerFuture = winnerFuture;
		}

		public TempoAtom getCurrentAtom() {
			return currentAtom;
		}

		public TempoAtom getAtom(AID aid) {
			return conflictingAtoms.get(aid);
		}

		public CompletableFuture<TempoAtom> getWinnerFuture() {
			return winnerFuture;
		}

		public Set<AID> getAids() {
			return conflictingAtoms.keySet();
		}
	}
}
