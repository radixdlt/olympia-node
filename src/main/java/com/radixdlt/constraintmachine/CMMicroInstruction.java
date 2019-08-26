package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.Particle;

public final class CMMicroInstruction {
	public enum CMOperation {
		PUSH,
		PARTICLE_GROUP
	}

	private final CMOperation operation;
	private final Particle particle;

	private CMMicroInstruction(CMOperation operation, Particle particle) {
		this.operation = operation;
		this.particle = particle;
	}

	public CMOperation getOperation() {
		return operation;
	}

	public Particle getParticle() {
		return particle;
	}

	public static CMMicroInstruction push(Particle particle) {
		return new CMMicroInstruction(CMOperation.PUSH, particle);
	}

	public static CMMicroInstruction particleGroup() {
		return new CMMicroInstruction(CMOperation.PARTICLE_GROUP, null);
	}
}
