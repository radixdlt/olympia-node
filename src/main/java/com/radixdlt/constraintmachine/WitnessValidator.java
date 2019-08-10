package com.radixdlt.constraintmachine;

import com.radixdlt.atomos.Result;
import com.radixdlt.atoms.Particle;

@FunctionalInterface
public interface WitnessValidator<T extends Particle> {
	/**
	 * @param fromParticle The particle we transition from
	 * @param metadata The metadata of the containing Atom
	 * @return A {@link Result} of the check
	 */
	Result validate(T fromParticle, AtomMetadata metadata);
}
