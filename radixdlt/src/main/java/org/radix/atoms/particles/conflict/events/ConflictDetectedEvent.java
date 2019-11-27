package org.radix.atoms.particles.conflict.events;

import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.events.Event;

public class ConflictDetectedEvent extends Event
{
	private ParticleConflict conflict;

	public ConflictDetectedEvent(ParticleConflict conflict) {
		super();
		this.conflict = conflict;
	}

	public ParticleConflict getConflict()
	{
		return this.conflict;
	}
}
