package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.ParticleGroup;
import java.util.Objects;
import java.util.Set;

/**
 * An error occuring in a Constraint Machine application procedure
 * TODO: Add error codes
 */
public final class ProcedureError {
	private final String errMsg;
	private final ImmutableSet<Long> particleIndices;

	ProcedureError(String errMsg, ImmutableSet<Long> particleIndices) {
		this.errMsg = Objects.requireNonNull(errMsg);
		this.particleIndices = Objects.requireNonNull(particleIndices);
	}

	public static ProcedureError of(ParticleGroup particleGroup, String errMsg, long particleIndex) {
		if (particleIndex >= particleGroup.getParticleCount()) {
			throw new IllegalArgumentException("Invalid particle index");
		}

		return new ProcedureError(errMsg, ImmutableSet.of(particleIndex));
	}

	public static ProcedureError of(String errMsg) {
		return new ProcedureError(errMsg, ImmutableSet.of());
	}

	public Set<Long> getParticleIndicies() {
		return particleIndices;
	}

	public String getErrMsg() {
		return errMsg;
	}

	@Override
	public String toString() {
		return errMsg + " " + particleIndices;
	}
}
