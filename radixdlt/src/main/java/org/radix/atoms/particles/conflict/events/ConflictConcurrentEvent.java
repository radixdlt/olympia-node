package org.radix.atoms.particles.conflict.events;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.radix.atoms.Atom;
import org.radix.atoms.particles.conflict.ParticleConflict;

public class ConflictConcurrentEvent extends ConflictEvent
{
	private final Set<Atom> concurrentAtoms = new HashSet<Atom>();

	public ConflictConcurrentEvent(ParticleConflict conflict, Set<Atom> concurrentAtoms)
	{
		super(conflict);

		this.concurrentAtoms.addAll(concurrentAtoms);
	}

	public Set<Atom> getConcurrentAtoms()
	{
		return Collections.unmodifiableSet(this.concurrentAtoms);
	}
}