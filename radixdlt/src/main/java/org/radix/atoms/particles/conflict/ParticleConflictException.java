package org.radix.atoms.particles.conflict;

import org.radix.exceptions.ValidationException;

import java.util.Objects;

@SuppressWarnings("serial")
public class ParticleConflictException extends ValidationException {
	private final ParticleConflict conflict;

	public ParticleConflictException(ParticleConflict conflict) {
		super(conflict.toString());

		this.conflict = Objects.requireNonNull(conflict);
	}

	public ParticleConflict getConflict() {
		return this.conflict;
	}
}
