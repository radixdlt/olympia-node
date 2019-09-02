package com.radixdlt.ledger.exceptions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerIndex;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An exception thrown when a unique key constraint is violated
 */
public class LedgerIndexConflictException extends LedgerException {
	private final Atom atom;
	private final ImmutableMap<LedgerIndex, Atom> conflictingAtoms;

	public LedgerIndexConflictException(Atom atom, ImmutableMap<LedgerIndex, Atom> conflictingAtoms) {
		super(getMessage(atom, conflictingAtoms));
		this.atom = Objects.requireNonNull(atom, "atom is required");
		this.conflictingAtoms = conflictingAtoms;
	}

	private static String getMessage(Atom atom, ImmutableMap<LedgerIndex, Atom> conflictingAtoms) {
		Objects.requireNonNull(conflictingAtoms, "conflictingAtoms is required");
		return String.format("Atom '%s' violated key constraints: %s", atom.getAID(), conflictingAtoms);
	}

	public ImmutableMap<LedgerIndex, Atom> getConflictingAtoms() {
		return conflictingAtoms;
	}

	public Atom getAtom() {
		return atom;
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
