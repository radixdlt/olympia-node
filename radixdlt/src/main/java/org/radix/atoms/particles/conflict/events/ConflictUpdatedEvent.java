package org.radix.atoms.particles.conflict.events;

import org.radix.atoms.particles.conflict.ParticleConflict;

public class ConflictUpdatedEvent extends ConflictEvent
{
	public ConflictUpdatedEvent(ParticleConflict conflict)
	{
		super(conflict);
	}
}