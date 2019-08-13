package com.radixdlt.atomos;

import com.radixdlt.atomos.SysCalls.WitnessValidator;
import com.radixdlt.atoms.Particle;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Formula defining a fungible transition
 */
public final class FungibleFormula {
	private final WitnessValidator<Particle> witnessValidator;
	private final BiPredicate<Particle, Particle> transition;

	FungibleFormula(
		WitnessValidator<Particle> witnessValidator,
		BiPredicate<? extends Particle, ? extends Particle> transition
	) {
		this.witnessValidator = Objects.requireNonNull(witnessValidator);
		this.transition = Objects.requireNonNull((BiPredicate<Particle, Particle>) transition);
	}

	public WitnessValidator<Particle> getWitnessValidator() {
		return witnessValidator;
	}

	public BiPredicate<Particle, Particle> getTransition() {
		return transition;
	}
}
