package com.radixdlt.atomos.procedures;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.AtomOS.ParticleClassWithSideEffectConstraintCheck;
import com.radixdlt.atomos.FungibleFormula;
import com.radixdlt.atomos.FungibleDefinition;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.ProcedureError;
import com.radixdlt.utils.UInt256;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Low-level implementation of fungible transition constraints.
 */
public class FungibleTransitionConstraintProcedure implements ConstraintProcedure {
	private final Map<Class<? extends Particle>, FungibleDefinition<? extends Particle>> fungibles;
	private final Map<Class<? extends Particle>, InputParticleProcedure> procedures = new HashMap<>();

	public FungibleTransitionConstraintProcedure(ImmutableMap<Class<? extends Particle>, FungibleDefinition<? extends Particle>> fungibles) {
		Objects.requireNonNull(fungibles);

		this.fungibles = fungibles;

		for (Entry<Class<? extends Particle>, FungibleDefinition<? extends Particle>> e : fungibles.entrySet()) {
			if (!fungibles.keySet().containsAll(e.getValue().getParticleClassToFormulaMap().keySet())) {
				throw new IllegalArgumentException("Outputs not all accounted for");
			}
		}

		this.fungibles.forEach((p, d) -> procedures.put(p, this::fungibleInputParticleExecutor));
	}

	private boolean fungibleInputParticleExecutor(Particle input, AtomMetadata metadata, Stack<Pair<Particle, Object>> outputs) {
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
			UInt256 outputAmount = (UInt256) top.getSecond();
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
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		final Stack<Pair<Particle, Object>> outputs = new Stack<>();

		for (int i = group.getParticleCount() - 1; i >= 0; i--) {
			SpunParticle sp = group.getSpunParticle(i);
			Particle p = sp.getParticle();
			if (sp.getSpin() == Spin.DOWN) {
				InputParticleProcedure inputParticleProcedure = this.procedures.get(p.getClass());
				if (inputParticleProcedure != null) {
					if (!inputParticleProcedure.execute(p, metadata, outputs)) {
						return Stream.of(ProcedureError.of("Input " + p + " failed. Output stack: " + outputs));
					}
				}
			} else if (sp.getSpin() == Spin.UP && this.fungibles.containsKey(p.getClass())) {
				outputs.push(Pair.of(p, fungibles.get(p.getClass()).mapToAmount(p)));
			}
		}

		if (!outputs.empty()) {
			final List<Particle> outputParticles = outputs.stream().map(Pair::getFirst).collect(Collectors.toList());
			final Set<Particle> otherOutput = group.particles(Spin.UP).collect(Collectors.toSet());
			for (Particle p : outputParticles) {
				Particle remove = null;
				FungibleDefinition<? extends Particle> transition = fungibles.get(p.getClass());
				if (transition != null && transition.getInitialWithConstraint() != null) {
					Class<? extends Particle> initialWithClass = transition.getInitialWithConstraint().getFirst();
					for (Particle other : otherOutput) {
						if (other.getClass() == initialWithClass) {
							ParticleClassWithSideEffectConstraintCheck<Particle, Particle> check = (ParticleClassWithSideEffectConstraintCheck<Particle, Particle>) transition
								.getInitialWithConstraint().getSecond();
							if (check.check(p, other, metadata).isSuccess()) {
								remove = other;
								break;
							}
						}
					}
				}

				if (remove != null) {
					otherOutput.remove(remove);
				} else {
					return Stream.of(ProcedureError.of("Fungible failure Output stack: " + outputs.toString()));
				}
			}
		}

		return Stream.empty();
	}
}