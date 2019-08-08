package com.radixdlt.atomos.procedures;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
	private final ImmutableSet<Class<? extends Particle>> inputTypes;
	private final ImmutableSet<Class<? extends Particle>> outputTypes;
	private final Map<Class<? extends Particle>, FungibleDefinition<? extends Particle>> fungibles;

	public FungibleTransitionConstraintProcedure(ImmutableMap<Class<? extends Particle>, FungibleDefinition<? extends Particle>> fungibles) {
		Objects.requireNonNull(fungibles);

		List<FungibleDefinition<? extends Particle>> fungibleDefinitions = fungibles.entrySet().stream()
			.map(Entry::getValue)
			.collect(Collectors.toList());

		this.fungibles = fungibles;
		this.inputTypes = fungibles.keySet();
		this.outputTypes = ImmutableSet.copyOf(fungibleDefinitions.stream()
			.flatMap(t -> t.getParticleClassToFormulaMap().keySet().stream())
			.collect(Collectors.toSet()));

		for (Entry<Class<? extends Particle>, FungibleDefinition<? extends Particle>> e : fungibles.entrySet()) {
			if (!fungibles.keySet().containsAll(e.getValue().getParticleClassToFormulaMap().keySet())) {
				throw new IllegalArgumentException("Outputs not all accounted for");
			}
		}
	}

	@Override
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		final Stack<Pair<Particle, UInt256>> inputs = new Stack<>();
		final Stack<Pair<Particle, UInt256>> outputs = new Stack<>();

		for (int i = group.getParticleCount() - 1; i >= 0; i--) {
			SpunParticle sp = group.getSpunParticle(i);
			Particle p = sp.getParticle();
			if (sp.getSpin() == Spin.DOWN && this.inputTypes.contains(p.getClass())) {
				UInt256 currentInput = fungibles.get(p.getClass()).mapToAmount(p);

				while (!currentInput.isZero()) {
					if (outputs.empty()) {
						break;
					}
					Pair<Particle, UInt256> top = outputs.peek();
					Particle toParticle = top.getFirst();
					FungibleFormula formula = fungibles.get(p.getClass()).getParticleClassToFormulaMap().get(toParticle.getClass());
					if (formula == null) {
						break;
					}
					if (!formula.getTransition().test(p, toParticle)) {
						break;
					}
					if (formula.getWitnessValidator().apply(p, metadata).isError()) {
						break;
					}

					outputs.pop();
					UInt256 outputAmount = top.getSecond();
					UInt256 min = UInt256.min(currentInput, outputAmount);
					UInt256 newOutputAmount = outputAmount.subtract(min);
					if (!newOutputAmount.isZero()) {
						outputs.push(Pair.of(toParticle, newOutputAmount));
					}

					currentInput = currentInput.subtract(min);
				}

				if (!currentInput.isZero()) {
					inputs.push(Pair.of(p, currentInput));
				}
			} else if (sp.getSpin() == Spin.UP && this.outputTypes.contains(p.getClass())) {
				outputs.push(Pair.of(p, fungibles.get(p.getClass()).mapToAmount(p)));
			}
		}

		if (!inputs.empty()) {
			return Stream.of(ProcedureError.of("Input stack not empty"));
		} else if (!outputs.empty()) {
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
					return Stream.of(ProcedureError.of("Output stack not empty"));
				}
			}
		}

		return Stream.empty();
	}
}