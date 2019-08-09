package com.radixdlt.atomos.procedures;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.FungibleFormula;
import com.radixdlt.atomos.FungibleDefinition;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.utils.UInt256;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Stack;

/**
 * Low-level implementation of fungible transition constraints.
 */
public class FungibleTransitionConstraintProcedure implements ConstraintProcedure {
	private final Map<Class<? extends Particle>, FungibleDefinition<? extends Particle>> fungibles;
	private final Map<Class<? extends Particle>, ParticleProcedure> procedures = new HashMap<>();

	public FungibleTransitionConstraintProcedure(ImmutableMap<Class<? extends Particle>, FungibleDefinition<? extends Particle>> fungibles) {
		Objects.requireNonNull(fungibles);

		this.fungibles = fungibles;

		for (Entry<Class<? extends Particle>, FungibleDefinition<? extends Particle>> e : fungibles.entrySet()) {
			if (!fungibles.keySet().containsAll(e.getValue().getParticleClassToFormulaMap().keySet())) {
				throw new IllegalArgumentException("Outputs not all accounted for");
			}
		}

		this.fungibles.forEach((p, d) -> procedures.put(p, new ParticleProcedure() {
			@Override
			public boolean inputExecute(Particle input, AtomMetadata metadata, Stack<Pair<Particle, Object>> outputs) {
				UInt256 currentInput = fungibles.get(input.getClass()).mapToAmount(input);

				while (!currentInput.isZero()) {
					if (outputs.empty()) {
						break;
					}
					Pair<Particle, Object> top = outputs.peek();
					Particle toParticle = top.getFirst();
					FungibleFormula formula = fungibles.get(input.getClass()).getParticleClassToFormulaMap().get(toParticle.getClass());
					if (formula == null) {
						break;
					}
					if (!formula.getTransition().test(input, toParticle)) {
						break;
					}
					if (formula.getWitnessValidator().validate(input, metadata).isError()) {
						break;
					}

					outputs.pop();
					Object outputMeta = top.getSecond();
					UInt256 outputAmount = outputMeta == null ? fungibles.get(toParticle.getClass()).mapToAmount(toParticle) : (UInt256) outputMeta;
					UInt256 min = UInt256.min(currentInput, outputAmount);
					UInt256 newOutputAmount = outputAmount.subtract(min);
					if (!newOutputAmount.isZero()) {
						outputs.push(Pair.of(toParticle, newOutputAmount));
					}

					currentInput = currentInput.subtract(min);
				}

				return currentInput.isZero();
			}

			@Override
			public boolean outputExecute(Particle output, AtomMetadata metadata) {
				return false;
			}
		}));
	}

	@Override
	public Map<Class<? extends Particle>, ParticleProcedure> getProcedures() {
		return procedures;
	}
}