package org.radix.atoms.particles.conflict.events;

import org.radix.atoms.particles.conflict.ParticleConflict;

public final class ConflictFailedEvent extends ConflictEvent
{
	public ConflictFailedEvent(ParticleConflict conflict)
	{
		super(conflict);
	}
}
