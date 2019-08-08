package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.AtomOS.ParticleClassWithSideEffectConstraintCheck;
import com.radixdlt.atomos.AtomOS.WitnessValidator;
import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * A fungible transition *from* one or multiple Particles types *to* a single Particle type
 *
 * @param <T> The target "to" type
 */
public final class FungibleTransition<T extends Particle> {
	private final Class<T> inputParticleClass;
	private final ParticleToAmountMapper<T> inputParticleToAmountMapper;
	private final Pair<Class<? extends Particle>, ParticleClassWithSideEffectConstraintCheck<T, ?>> initialWithConstraint;
	private final Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap;

	private FungibleTransition(
		Class<T> inputParticleClass,
		ParticleToAmountMapper<T> inputParticleToAmountMapper,
		Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap,
		Pair<Class<? extends Particle>, ParticleClassWithSideEffectConstraintCheck<T, ?>> initialWithConstraint
	) {
		this.inputParticleClass = inputParticleClass;
		this.inputParticleToAmountMapper = inputParticleToAmountMapper;
		this.particleTypeToFormulasMap = particleTypeToFormulasMap;
		this.initialWithConstraint = initialWithConstraint;
	}

	public Class<T> getInputParticleClass() {
		return this.inputParticleClass;
	}

	public ParticleToAmountMapper<T> getInputParticleToAmountMapper() {
		return inputParticleToAmountMapper;
	}

	public Map<Class<? extends Particle>, FungibleFormula> getParticleClassToFormulaMap() {
		return this.particleTypeToFormulasMap;
	}

	public Pair<Class<? extends Particle>, ParticleClassWithSideEffectConstraintCheck<T, ?>> getInitialWithConstraint() {
		return this.initialWithConstraint;
	}

	public static <T extends Particle> FungibleTransition<T> from(
		Class<T> outputClass,
		ParticleToAmountMapper<T> outputToAmountMapper,
		Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap
	) {

		Objects.requireNonNull(outputClass, "outputClass is required");
		Objects.requireNonNull(outputToAmountMapper, "outputToAmountMapper is required");
		Objects.requireNonNull(particleTypeToFormulasMap);

		if (particleTypeToFormulasMap.isEmpty()) {
			throw new IllegalArgumentException("One of formulas or initial constraint must be defined");
		}

		return new FungibleTransition<>(outputClass, outputToAmountMapper, particleTypeToFormulasMap, null);
	}


	public static <T extends Particle> FungibleTransition<T> from(
		Class<T> outputClass,
		ParticleToAmountMapper<T> outputToAmountMapper,
		Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap,
		Pair<Class<? extends Particle>, ParticleClassWithSideEffectConstraintCheck<T, ?>> initialWithConstraint
	) {

		Objects.requireNonNull(outputClass, "outputClass is required");
		Objects.requireNonNull(outputToAmountMapper, "outputToAmountMapper is required");
		Objects.requireNonNull(particleTypeToFormulasMap);

		if (particleTypeToFormulasMap.isEmpty()) {
			throw new IllegalArgumentException("One of formulas or initial constraint must be defined");
		}

		return new FungibleTransition<>(outputClass, outputToAmountMapper, particleTypeToFormulasMap, initialWithConstraint);
	}

	public static class Builder<T extends Particle> {
		private Class<T> inputParticleClass;
		private ParticleToAmountMapper<T> inputParticleToAmountMapper;
		private final ImmutableMap.Builder<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMapBuilder = new ImmutableMap.Builder<>();
		private Pair<Class<? extends Particle>, ParticleClassWithSideEffectConstraintCheck<T, ?>> initialWithConstraint;

		public Builder() {
		}

		public Builder<T> from(
			Class<T> inputParticleClass,
			ParticleToAmountMapper<T> inputParticleToAmountMapper
		) {
			this.inputParticleClass = inputParticleClass;
			this.inputParticleToAmountMapper = inputParticleToAmountMapper;

			return this;
		}

		public <U extends Particle> Builder<T> initialWith(Class<U> sideEffectClass, ParticleClassWithSideEffectConstraintCheck<T, U> constraint) {
			this.initialWithConstraint = new Pair<>(sideEffectClass, constraint);
			return this;
		}

		public <U extends Particle> Builder<T> to(
			Class<U> toParticleClass,
			WitnessValidator<T> witnessValidator,
			BiPredicate<T, U> transition
		) {
			particleTypeToFormulasMapBuilder.put(toParticleClass, new FungibleFormula((WitnessValidator<Particle>)witnessValidator, transition));

			return this;
		}

		public FungibleTransition<T> build() {
			Objects.requireNonNull(inputParticleClass, "Unfinished transition, output class must be defined.");
			Objects.requireNonNull(inputParticleToAmountMapper, "Unfinished transition, output amount mapper must be defined.");

			Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap = particleTypeToFormulasMapBuilder.build();

			if (particleTypeToFormulasMap.isEmpty() && initialWithConstraint == null) {
				throw new IllegalStateException("Unfinished transition, no formulas added and no initial constraint defined.");
			}

			return FungibleTransition.from(
				inputParticleClass,
				inputParticleToAmountMapper,
				particleTypeToFormulasMap,
				initialWithConstraint
			);
		}
	}
}
