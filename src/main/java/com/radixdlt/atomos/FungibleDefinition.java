package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.AtomOS.WitnessValidator;
import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

public final class FungibleDefinition<T extends Particle> {
	private final Class<T> inputParticleClass;
	private final ParticleToAmountMapper<T> inputParticleToAmountMapper;
	private final Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap;

	private FungibleDefinition(
		Class<T> inputParticleClass,
		ParticleToAmountMapper<T> inputParticleToAmountMapper,
		Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap
	) {
		this.inputParticleClass = inputParticleClass;
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

		return new FungibleDefinition<>(outputClass, outputToAmountMapper, particleTypeToFormulasMap);
	}

	public static class Builder<T extends Particle> {
		private Class<T> inputParticleClass;
		private ParticleToAmountMapper<T> inputParticleToAmountMapper;
		private final ImmutableMap.Builder<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMapBuilder = new ImmutableMap.Builder<>();

		public Builder() {
		}

		public Builder<T> of(
			Class<T> inputParticleClass,
			ParticleToAmountMapper<T> inputParticleToAmountMapper
		) {
			this.inputParticleClass = inputParticleClass;
			this.inputParticleToAmountMapper = inputParticleToAmountMapper;

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

		public FungibleDefinition<T> build() {
			Objects.requireNonNull(inputParticleClass, "Unfinished transition, output class must be defined.");
			Objects.requireNonNull(inputParticleToAmountMapper, "Unfinished transition, output amount mapper must be defined.");

			Map<Class<? extends Particle>, FungibleFormula> particleTypeToFormulasMap = particleTypeToFormulasMapBuilder.build();

			if (particleTypeToFormulasMap.isEmpty()) {
				throw new IllegalStateException("Unfinished transition, no formulas added and no initial constraint defined.");
			}

			return FungibleDefinition.of(
				inputParticleClass,
				inputParticleToAmountMapper,
				particleTypeToFormulasMap
			);
		}
	}
}
