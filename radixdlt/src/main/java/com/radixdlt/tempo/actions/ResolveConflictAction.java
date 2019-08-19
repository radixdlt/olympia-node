package com.radixdlt.tempo.actions;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

public class ResolveConflictAction implements TempoAction {
	private final TempoAtom atom;
	private final Set<TempoAtom> conflictingAtoms;
	private final EUID tag;

	public ResolveConflictAction(TempoAtom atom, Collection<TempoAtom> conflictingAtoms, EUID tag) {
		this.atom = atom;
		this.conflictingAtoms = ImmutableSet.copyOf(conflictingAtoms);
		this.tag = tag;
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
		return tag;
	}
}
