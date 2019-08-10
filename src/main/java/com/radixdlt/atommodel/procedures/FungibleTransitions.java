package com.radixdlt.atommodel.procedures;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.utils.UInt256;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Low-level implementation of fungible transition constraints.
 */
public class FungibleTransitions implements ConstraintProcedure {

	private final ImmutableMap<Class<? extends Particle>, FungibleDefinition<? extends Particle>> fungibles;

	public FungibleTransitions(ImmutableMap<Class<? extends Particle>, FungibleDefinition<? extends Particle>> fungibles) {
		this.fungibles = fungibles;

		for (Entry<Class<? extends Particle>, FungibleDefinition<? extends Particle>> e : fungibles.entrySet()) {
			if (!fungibles.keySet().containsAll(e.getValue().getParticleClassToFormulaMap().keySet())) {
				throw new IllegalArgumentException("Outputs not all accounted for");
			}
		}
	}

	@Override
	public ImmutableSet<Pair<Class<? extends Particle>, Class<? extends Particle>>> supports() {
		return fungibles.entrySet().stream()
			.flatMap(in -> in.getValue().getParticleClassToFormulaMap().keySet().stream()
				.map(out -> Pair.<Class<? extends Particle>, Class<? extends Particle>>of(in.getKey(), out)))
			.collect(ImmutableSet.toImmutableSet());
	}

	@Override
	public boolean validateWitness(
		ProcedureResult result,
		Particle inputParticle,
		Particle outputParticle,
		AtomMetadata metadata
	) {
		switch (result) {
			case POP_OUTPUT:
				return true;
			case POP_INPUT:
			case POP_INPUT_OUTPUT:
				return fungibles.get(inputParticle.getClass())
					.getParticleClassToFormulaMap()
					.get(outputParticle.getClass())
					.getWitnessValidator()
					.validate(inputParticle, metadata)
					.isSuccess();
			default:
				throw new IllegalStateException();
		}
	}

	@Override
	public ProcedureResult execute(
		Particle inputParticle,
		AtomicReference<Object> inputData,
		Particle outputParticle,
		AtomicReference<Object> outputData
	) {
		FungibleFormula formula = fungibles.get(inputParticle.getClass()).getParticleClassToFormulaMap().get(outputParticle.getClass());
		if (!formula.getTransition().test(inputParticle, outputParticle)) {
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
}