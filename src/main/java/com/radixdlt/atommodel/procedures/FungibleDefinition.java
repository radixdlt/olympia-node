package com.radixdlt.atommodel.procedures;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.utils.UInt256;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Defines how a particle maps for fungibility
 *
 * @param <T> particle type
 */
public final class FungibleDefinition<T extends Particle> {
	private final ParticleToAmountMapper<T> inputParticleToAmountMapper;
	private final Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap;

	private FungibleDefinition(
		ParticleToAmountMapper<T> inputParticleToAmountMapper,
		Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap
	) {
		this.inputParticleToAmountMapper = inputParticleToAmountMapper;
		this.particleTypeToFormulasMap = particleTypeToFormulasMap;
	}

	public UInt256 mapToAmount(Particle t) {
		return inputParticleToAmountMapper.amount((T) t);
	}

	public Map<Class<? extends Particle>, FungibleFormula> getParticleClassToFormulaMap() {
		return this.particleTypeToFormulasMap;
	}

	public static <T extends Particle> FungibleDefinition<T> of(
		ParticleToAmountMapper<T> outputToAmountMapper,
		Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap
	) {

		Objects.requireNonNull(outputToAmountMapper, "outputToAmountMapper is required");
		Objects.requireNonNull(particleTypeToFormulasMap);

		if (particleTypeToFormulasMap.isEmpty()) {
			throw new IllegalArgumentException("One of formulas or initial constraint must be defined");
		}

		return new FungibleDefinition<>(outputToAmountMapper, particleTypeToFormulasMap);
	}

	public static class Builder<T extends Particle> {
		private ParticleToAmountMapper<T> inputParticleToAmountMapper;
		private final ImmutableMap.Builder<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMapBuilder = new ImmutableMap.Builder<>();

		public Builder() {
		}

		public Builder<T> amountMapper(ParticleToAmountMapper<T> inputParticleToAmountMapper) {
			this.inputParticleToAmountMapper = inputParticleToAmountMapper;
			return this;
		}

		public <U extends Particle> Builder<T> to(
			Class<U> toParticleClass,
			BiPredicate<T, U> transition,
			WitnessValidator<T> witnessValidator
		) {
			particleTypeToFormulasMapBuilder.put(toParticleClass, new FungibleFormula((WitnessValidator<Particle>)witnessValidator, transition));
			return this;
		}

		public FungibleDefinition<T> build() {
			Objects.requireNonNull(inputParticleToAmountMapper, "Unfinished transition, output amount mapper must be defined.");

			Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap = particleTypeToFormulasMapBuilder.build();

			if (particleTypeToFormulasMap.isEmpty()) {
				throw new IllegalStateException("Unfinished transition, no formulas added and no initial constraint defined.");
			}

			return FungibleDefinition.of(
				inputParticleToAmountMapper,
				particleTypeToFormulasMap
			);
		}
	}
}
