package com.radixdlt.tempo.store;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerIndex;

import java.util.stream.Stream;

public final class AtomConflict {
	private final Atom atom;
	private final ImmutableMap<LedgerIndex, Atom> conflictingAtoms;

	public AtomConflict(Atom atom, ImmutableMap<LedgerIndex, Atom> conflictingAtoms) {
		this.atom = atom;
		this.conflictingAtoms = conflictingAtoms;
	}

	public Atom getAtom() {
		return atom;
	}

	public ImmutableMap<LedgerIndex, Atom> getConflictingAtoms() {
		return conflictingAtoms;
	}

	public ImmutableSet<AID> getConflictingAids() {
		return conflictingAtoms.values().stream()
			.map(Atom::getAID)
			.collect(ImmutableSet.toImmutableSet());
	}

	public ImmutableSet<AID> getAllAids() {
		return Stream.concat(Stream.of(atom), conflictingAtoms.values().stream())
			.map(Atom::getAID)
			.collect(ImmutableSet.toImmutableSet());
	}
}
