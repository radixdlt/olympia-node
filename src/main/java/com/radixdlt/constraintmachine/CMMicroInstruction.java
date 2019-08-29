package com.radixdlt.constraintmachine;

public final class CMMicroInstruction {
	public enum CMMicroOp {
		CHECK_NEUTRAL,
		CHECK_UP,
		PUSH,
		PARTICLE_GROUP
	}

	private final CMMicroOp operation;
	private final Particle particle;

	private CMMicroInstruction(CMMicroOp operation, Particle particle) {
		this.operation = operation;
		this.particle = particle;
	}

	public CMMicroOp getMicroOp() {
		return operation;
	}

	public Particle getParticle() {
		return particle;
	}

	public boolean isCheckSpin() {
		return operation == CMMicroOp.CHECK_NEUTRAL || operation == CMMicroOp.CHECK_UP;
	}

	public Spin getCheckSpin() {
		if (operation == CMMicroOp.CHECK_NEUTRAL) {
			return Spin.NEUTRAL;
		} else if (operation == CMMicroOp.CHECK_UP) {
			return Spin.UP;
		} else {
			throw new UnsupportedOperationException(operation + " is not a check spin operation.");
		}
	}

	public static CMMicroInstruction push(Particle particle) {
		return new CMMicroInstruction(CMMicroOp.PUSH, particle);
	}

	public static CMMicroInstruction checkSpin(Particle particle, Spin spin) {
		if (spin == Spin.NEUTRAL) {
			return new CMMicroInstruction(CMMicroOp.CHECK_NEUTRAL, particle);
		} else if (spin == Spin.UP) {
			return new CMMicroInstruction(CMMicroOp.CHECK_UP, particle);
		} else {
			throw new IllegalStateException("Invalid check spin: " + spin);
		}
	}

	public static CMMicroInstruction particleGroup() {
		return new CMMicroInstruction(CMMicroOp.PARTICLE_GROUP, null);
	}
}
