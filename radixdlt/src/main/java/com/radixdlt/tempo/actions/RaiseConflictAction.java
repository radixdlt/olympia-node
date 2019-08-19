package com.radixdlt.tempo.actions;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.utils.UInt128;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

public class RaiseConflictAction implements TempoAction {
	private final TempoAtom atom;
	private final Set<TempoAtom> conflictingAtoms;

	public RaiseConflictAction(TempoAtom atom, Collection<TempoAtom> conflictingAtoms) {
		this.atom = atom;
		this.conflictingAtoms = ImmutableSet.copyOf(conflictingAtoms);
	}

	public TempoAtom getAtom() {
		return atom;
	}

	public Set<TempoAtom> getConflictingAtoms() {
		return conflictingAtoms;
	}

	public Stream<TempoAtom> allAtoms() {
		return Stream.concat(Stream.of(atom), conflictingAtoms.stream());
	}

	public Stream<AID> allAids() {
		return allAtoms().map(TempoAtom::getAID);
	}

	public EUID getTag() {
		return new EUID(allAids()
			.map(AID::getBytes)
			.map(UInt128::from)
			.reduce(UInt128.ZERO, UInt128::add));
	}
}
