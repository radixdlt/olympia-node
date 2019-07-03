package org.radix.atoms.particles.conflict.events;

import org.radix.atoms.particles.conflict.ParticleConflict;

public final class ConflictResolvedEvent extends ConflictEvent
{
	public ConflictResolvedEvent(ParticleConflict conflict)
	{
		super(conflict);
	}
}
