package com.radixdlt.atomos;

import com.radixdlt.atomos.AtomOS.FungibleTransitionInputConstraint;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.Objects;

/**
 * A member of a {@link FungibleFormula} from a set of types to a single target type.
 * @param <T> The type of the member
 */
public final class FungibleTransitionMember<T extends Particle> {
	private final Class<T> particleClass;
	private final FungibleTransitionInputConstraint<T, ? extends Particle> constraint;

	public FungibleTransitionMember(Class<T> particleClass,
	                                FungibleTransitionInputConstraint<T, ? extends Particle> constraint) {
		this.particleClass = Objects.requireNonNull(particleClass, "particleClass is required");
		this.constraint = Objects.requireNonNull(constraint, "constraint is required");
	}

	public Class<T> particleClass() {
		return this.particleClass;
	}

	public Result check(Particle fromParticle, Particle toParticle, AtomMetadata metadata) {
		return ((FungibleTransitionInputConstraint) this.constraint).apply(fromParticle, toParticle, metadata);
	}
}
