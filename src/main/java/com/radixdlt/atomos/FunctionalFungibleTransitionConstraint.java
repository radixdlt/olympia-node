package com.radixdlt.atomos;

import com.radixdlt.atomos.AtomOS.FungibleTransitionConstraint;
import com.radixdlt.atomos.AtomOS.ParticleClassWithSideEffectConstraintCheck;
import com.radixdlt.atomos.AtomOS.ParticleRequireWithStub;
import com.radixdlt.atoms.Particle;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A helper class for constructing a Fungible Transition Constraint
 *
 * @param <T> particle class to add constraints to
 */
public final class FunctionalFungibleTransitionConstraint<T extends Particle> implements AtomOS.FungibleTransitionConstraintStub<T>, FungibleTransitionConstraint<T> {
	private final Consumer<FungibleFormula> formulaHandler;
	private final Consumer<AtomOS.FungibleTransitionInitialConstraint<T>> initialHandler;
	private final ParticleRequireWithStub<T> initialRequireWithHandler;

	public FunctionalFungibleTransitionConstraint(
		Consumer<AtomOS.FungibleTransitionInitialConstraint<T>> initialHandler,
		ParticleRequireWithStub<T> initialRequireWithHandler,
		Consumer<FungibleFormula> formulaHandler
	) {
		this.formulaHandler = Objects.requireNonNull(formulaHandler);
		this.initialRequireWithHandler = Objects.requireNonNull(initialRequireWithHandler);
		this.initialHandler = Objects.requireNonNull(initialHandler);
	}

	@Override
	public FungibleTransitionConstraint<T> requireInitial(AtomOS.FungibleTransitionInitialConstraint<T> check) {
		initialHandler.accept(check);

		return this;
	}


	@Override
	public <U extends Particle> FungibleTransitionConstraint<T> requireInitialWith(
		Class<U> sideEffectClass,
		ParticleClassWithSideEffectConstraintCheck<T, U> constraint
	) {
		initialRequireWithHandler.requireWith(sideEffectClass, constraint);

		return this;
	}

	@Override
	public FungibleTransitionConstraint<T> requireFrom(FungibleFormula formula) {
		formulaHandler.accept(formula);

		return this;
	}

	@Override
	public FungibleTransitionConstraint<T> orFrom(FungibleFormula formula) {
		formulaHandler.accept(formula);

		return this;
	}
}
