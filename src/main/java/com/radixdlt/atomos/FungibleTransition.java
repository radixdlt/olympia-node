package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.radixdlt.atomos.AtomOS.ParticleClassWithSideEffectConstraintCheck;
import com.radixdlt.atomos.AtomOS.WitnessValidator;
import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atomos.procedures.fungible.Fungible;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * A fungible transition *from* one or multiple Particles types *to* a single Particle type
 *
 * @param <T> The target "to" type
 */
public final class FungibleTransition<T extends Particle> {
	private final Class<T> outputParticleClass;
	private final ParticleToAmountMapper<T> outputParticleToAmountMapper;
	private final Pair<Class<? extends Particle>, ParticleClassWithSideEffectConstraintCheck<T, ?>> initialWithConstraint;
	private final Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap;

	private FungibleTransition(
		Class<T> outputParticleClass,
		ParticleToAmountMapper<T> outputParticleToAmountMapper,
		Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap,
		Pair<Class<? extends Particle>, ParticleClassWithSideEffectConstraintCheck<T, ?>> initialWithConstraint
	) {
		this.outputParticleClass = outputParticleClass;
		this.outputParticleToAmountMapper = outputParticleToAmountMapper;
		this.particleTypeToFormulasMap = particleTypeToFormulasMap;
		this.initialWithConstraint = initialWithConstraint;
	}

	public Class<T> getOutputParticleClass() {
		return this.outputParticleClass;
	}

	public ParticleToAmountMapper<T> getOutputParticleToAmountMapper() {
		return outputParticleToAmountMapper;
	}

	public Set<Class<? extends Particle>> getAllInputs() {
		return this.particleTypeToFormulasMap.keySet();
	}

	// Don't check initialWithConstraint here as it requires a lot more info and state of atom
	// FIXME: cleanup/refactor this mess!
	public FungibleTransitionInitialVerdict checkInitial(Fungible to, AtomMetadata metadata) {
		if (!this.hasInitial()) {
			throw new IllegalStateException("Transition does not have initial to check.");
		}

		return new FungibleTransitionInitialVerdict(to, Result.success());
	}

	public Map<Class<? extends Particle>, FungibleFormula> getParticleClassToFormulaMap() {
		return this.particleTypeToFormulasMap;
	}

	public Pair<Class<? extends Particle>, ParticleClassWithSideEffectConstraintCheck<T, ?>> getInitialWithConstraint() {
		return this.initialWithConstraint;
	}

	public boolean hasInitial() {
		return this.initialWithConstraint != null;
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

	public static <T extends Particle> Builder<T> build() {
		return new Builder<>();
	}

	/**
	 * The verdict of trying a fungible as an initial to this transition
	 */
	public static class FungibleTransitionInitialVerdict {
		private final Fungible output;
		private final Result result;

		private FungibleTransitionInitialVerdict(Fungible output, Result result) {
			this.output = Objects.requireNonNull(output);
			this.result = Objects.requireNonNull(result);
		}

		public Fungible getOutput() {
			return this.output;
		}

		public boolean isApproval() {
			return this.result.isSuccess();
		}

		public boolean isRejection() {
			return this.result.isError();
		}

		public Optional<String> getRejectionMessage() {
			return this.result.getErrorMessage();
		}
	}

	public static class Builder<T extends Particle> {
		private Class<T> outputParticleClass;
		private ParticleToAmountMapper<T> outputParticleToAmountMapper;
		private final ImmutableMap.Builder<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMapBuilder = new ImmutableMap.Builder<>();
		private Pair<Class<? extends Particle>, ParticleClassWithSideEffectConstraintCheck<T, ?>> initialWithConstraint;

		private Builder() {
		}

		public Builder<T> to(
			Class<T> outputParticleClass,
			ParticleToAmountMapper<T> outputParticleToAmountMapper
		) {
			this.outputParticleClass = outputParticleClass;
			this.outputParticleToAmountMapper = outputParticleToAmountMapper;

			return this;
		}

		public <U extends Particle> Builder<T> initialWith(Class<U> sideEffectClass, ParticleClassWithSideEffectConstraintCheck<T, U> constraint) {
			this.initialWithConstraint = new Pair<>(sideEffectClass, constraint);
			return this;
		}

		public <U extends Particle> Builder<T> from(
			Class<U> fromParticleClass,
			WitnessValidator<U> witnessValidator,
			BiPredicate<U, T> transition
		) {
			particleTypeToFormulasMapBuilder.put(fromParticleClass, new FungibleFormula((WitnessValidator<Particle>)witnessValidator, transition));

			return this;
		}

		public FungibleTransition<T> build() {
			Objects.requireNonNull(outputParticleClass, "Unfinished transition, output class must be defined.");
			Objects.requireNonNull(outputParticleToAmountMapper, "Unfinished transition, output amount mapper must be defined.");

			Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap = particleTypeToFormulasMapBuilder.build();

			if (particleTypeToFormulasMap.isEmpty() && initialWithConstraint == null) {
				throw new IllegalStateException("Unfinished transition, no formulas added and no initial constraint defined.");
			}

			return FungibleTransition.from(
				outputParticleClass,
				outputParticleToAmountMapper,
				particleTypeToFormulasMap,
				initialWithConstraint
			);
		}
	}
}
