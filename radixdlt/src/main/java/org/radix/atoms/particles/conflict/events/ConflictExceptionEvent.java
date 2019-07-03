package org.radix.atoms.particles.conflict.events;

import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.exceptions.ExceptionEvent;

public class ConflictExceptionEvent extends ExceptionEvent
{
	private final ParticleConflict conflict;

	public ConflictExceptionEvent(Throwable throwable, ParticleConflict conflict)
	{
		super(throwable);

		this.conflict = conflict;
	}

	public ParticleConflict getConflict()
	{
		return this.conflict;
	}
}
