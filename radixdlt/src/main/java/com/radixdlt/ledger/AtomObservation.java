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
		RECEIVE,
		COMMIT
	}

	private final Type type;
	private final Atom atom;
	private final Set<Atom> previousAtoms;

	private AtomObservation(Type type, Atom atom, Set<Atom> previousAtoms) {
		this.type = type;
		this.atom = atom;
		this.previousAtoms = previousAtoms;
	}

	public Type getType() {
		return type;
	}

	public Atom getAtom() {
		return atom;
	}

	public boolean hasPreviousAtoms() {
		return !previousAtoms.isEmpty();
	}

	public Set<Atom> getPreviousAtoms() {
		return previousAtoms;
	}

	public static AtomObservation receive(Atom newAtom) {
		Objects.requireNonNull(newAtom, "newAtom is required");
		return new AtomObservation(Type.RECEIVE, newAtom, ImmutableSet.of());
	}

	public static AtomObservation change(Set<Atom> previousAtoms, Atom newAtom) {
		Objects.requireNonNull(previousAtoms, "previousAtoms is required");
		Objects.requireNonNull(newAtom, "newAtom is required");
		return new AtomObservation(Type.RECEIVE, newAtom, previousAtoms);
	}

	public static AtomObservation commit(Atom atom) {
		Objects.requireNonNull(atom, "atom is required");
		return new AtomObservation(Type.COMMIT, atom, ImmutableSet.of());
	}
}
