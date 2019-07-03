package com.radixdlt.constraintmachine;

/**
 * Error codes from Constraint Machine validation.
 * TODO: add numeric codes
 */
public enum CMErrorCode {
	EMPTY_PARTICLE_GROUP("Empty particle group"),
	DUPLICATE_PARTICLES_IN_GROUP("Duplicate particles in group"),
	INTERNAL_SPIN_CONFLICT("Internal spin conflict"),
	INTERNAL_SPIN_MISSING_DEPENDENCY("Internal spin missing dependency"),
	ILLEGAL_SPIN_TRANSITION("Illegal spin transition"),
	UNKNOWN_PARTICLE("Unknown particle"),
	NO_DESTINATION_PARTICLE("Particle has no destination"),
	KERNEL_ERROR("Kernel error"),
	PROCEDURE_ERROR("Procedure error");

	private final String description;

	CMErrorCode(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}
}
