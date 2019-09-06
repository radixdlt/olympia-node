package com.radixdlt.ledger;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;

import java.util.Objects;
import java.util.Set;

/**
 * Observation of an {@link Atom} made in a {@link Ledger}
 */
public final class LedgerObservation {
	public enum Type {
		ADOPT,
		COMMIT
	}

	private final Type type;
	private final Atom atom;
	private final Set<Atom> supersededAtoms;

	private LedgerObservation(Type type, Atom atom, Set<Atom> supersededAtoms) {
		this.type = type;
		this.atom = atom;
		this.supersededAtoms = supersededAtoms;
	}

	public Type getType() {
		return type;
	}

	public Atom getAtom() {
		return atom;
	}

	public boolean hasSupersededAtoms() {
		return !supersededAtoms.isEmpty();
	}

	public Set<Atom> getSupersededAtoms() {
		return supersededAtoms;
	}

	public static LedgerObservation adopt(Atom newAtom) {
		Objects.requireNonNull(newAtom, "newAtom is required");
		return new LedgerObservation(Type.ADOPT, newAtom, ImmutableSet.of());
	}

	public static LedgerObservation adopt(Set<Atom> supersededAtoms, Atom newAtom) {
		Objects.requireNonNull(supersededAtoms, "supersededAtoms is required");
		Objects.requireNonNull(newAtom, "newAtom is required");
		return new LedgerObservation(Type.ADOPT, newAtom, supersededAtoms);
	}

	public static LedgerObservation commit(Atom atom) {
		Objects.requireNonNull(atom, "atom is required");
		return new LedgerObservation(Type.COMMIT, atom, ImmutableSet.of());
	}
}
