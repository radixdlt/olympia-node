package com.radixdlt.atomos;

import com.radixdlt.atomos.AtomOS.WitnessValidator;
import com.radixdlt.atoms.Particle;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Formula defining a fungible transition *from* a set of types *to* a target type with a certain composition
 */
public final class FungibleFormula {
	private final WitnessValidator<? extends Particle> witnessValidator;
	private final BiPredicate<Particle, Particle> transition;

	FungibleFormula(
		WitnessValidator<? extends Particle> witnessValidator,
		BiPredicate<? extends Particle, ? extends Particle> transition
	) {
		this.witnessValidator = Objects.requireNonNull(witnessValidator, "constraint is required");
		this.transition = Objects.requireNonNull((BiPredicate<Particle, Particle>) transition);
	}

	public WitnessValidator<? extends Particle> getWitnessValidator() {
		return witnessValidator;
	}

	public BiPredicate<Particle, Particle> getTransition() {
		return transition;
	}
}
