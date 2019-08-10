package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ParticleProcedure;
import com.radixdlt.utils.UInt256;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Low-level implementation of fungible transition constraints.
 */
public class FungibleParticlesProcedureBuilder {

	private final ImmutableMap.Builder<Class<? extends Particle>, FungibleDefinition<? extends Particle>> fungibleDefinitionBuilder = new Builder<>();

	public FungibleParticlesProcedureBuilder() {
	}

	public <T extends Particle> FungibleParticlesProcedureBuilder add(Class<T> particleClass, FungibleDefinition<? extends Particle> definition) {
		fungibleDefinitionBuilder.put(particleClass, definition);
		return this;
	}

	public Map<Class<? extends Particle>, ParticleProcedure> build() {
		final Map<Class<? extends Particle>, FungibleDefinition<? extends Particle>> fungibles = fungibleDefinitionBuilder.build();

		for (Entry<Class<? extends Particle>, FungibleDefinition<? extends Particle>> e : fungibles.entrySet()) {
			if (!fungibles.keySet().containsAll(e.getValue().getParticleClassToFormulaMap().keySet())) {
				throw new IllegalArgumentException("Outputs not all accounted for");
			}
		}

		final Map<Class<? extends Particle>, ParticleProcedure> procedures = new HashMap<>();
		fungibles.forEach((p, d) -> procedures.put(p, new ParticleProcedure() {
			@Override
			public ProcedureResult execute(
				Particle inputParticle,
				AtomicReference<Object> inputData,
				Particle outputParticle,
				AtomicReference<Object> outputData,
				AtomMetadata metadata
			) {
				if (!fungibles.containsKey(outputParticle.getClass())) {
					return ProcedureResult.ERROR;
				}

				FungibleFormula formula = fungibles.get(inputParticle.getClass()).getParticleClassToFormulaMap().get(outputParticle.getClass());
				if (formula == null) {
					return ProcedureResult.ERROR;
				}

				if (!formula.getTransition().test(inputParticle, outputParticle)) {
					return ProcedureResult.ERROR;
				}

				if (formula.getWitnessValidator().validate(inputParticle, metadata).isError()) {
					return ProcedureResult.ERROR;
				}

				UInt256 inputAmount = inputData.get() == null
					? fungibles.get(inputParticle.getClass()).mapToAmount(inputParticle)
					: (UInt256) inputData.get();
				UInt256 outputAmount = outputData.get() == null
					? fungibles.get(outputParticle.getClass()).mapToAmount(outputParticle)
					: (UInt256) outputData.get();

				int compare = inputAmount.compareTo(outputAmount);
				if (compare == 0) {
					return ProcedureResult.POP_INPUT_OUTPUT;
				} else if (compare > 0) {
					inputData.set(inputAmount.subtract(outputAmount));
					return ProcedureResult.POP_OUTPUT;
				} else {
					outputData.set(outputAmount.subtract(inputAmount));
					return ProcedureResult.POP_INPUT;
				}
			}

			@Override
			public boolean outputExecute(Particle output, AtomMetadata metadata) {
				return false;
			}
		}));
		return procedures;
	}
}