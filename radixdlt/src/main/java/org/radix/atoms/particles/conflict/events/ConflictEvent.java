package org.radix.atoms.particles.conflict.events;

import org.radix.atoms.events.ParticleEvent;
import org.radix.atoms.particles.conflict.ParticleConflict;

public abstract class ConflictEvent extends ParticleEvent
{
	private ParticleConflict conflict;

	public ConflictEvent(ParticleConflict conflict)
	{
		super(conflict.getSpunParticle());

		this.conflict = conflict;
	}

	public ParticleConflict getConflict()
	{
		return this.conflict;
	}
}
