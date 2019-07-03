package org.radix.atoms.particles.conflict.events;

import org.radix.atoms.particles.conflict.ParticleConflict;

public class ConflictDetectedEvent extends ConflictEvent
{
	public ConflictDetectedEvent(ParticleConflict conflict)
	{
		super(conflict);
	}
}
