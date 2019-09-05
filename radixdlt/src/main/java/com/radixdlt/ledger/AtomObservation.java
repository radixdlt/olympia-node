package com.radixdlt.ledger;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;

import java.util.Objects;
import java.util.Set;

/**
 * Observation of an {@link Atom} made in a {@link Ledger}
 */
public final class AtomObservation {
	public enum Type {
		ADOPT,
		COMMIT
	}

	private final Type type;
	private final Atom atom;
	private final Set<Atom> supersededAtoms;

	private AtomObservation(Type type, Atom atom, Set<Atom> supersededAtoms) {
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

	public static AtomObservation adopt(Atom newAtom) {
		Objects.requireNonNull(newAtom, "newAtom is required");
		return new AtomObservation(Type.ADOPT, newAtom, ImmutableSet.of());
	}

	public static AtomObservation adopt(Set<Atom> previousAtoms, Atom newAtom) {
		Objects.requireNonNull(previousAtoms, "supersededAtoms is required");
		Objects.requireNonNull(newAtom, "newAtom is required");
		return new AtomObservation(Type.ADOPT, newAtom, previousAtoms);
	}

	public static AtomObservation commit(Atom atom) {
		Objects.requireNonNull(atom, "atom is required");
		return new AtomObservation(Type.COMMIT, atom, ImmutableSet.of());
	}
}
