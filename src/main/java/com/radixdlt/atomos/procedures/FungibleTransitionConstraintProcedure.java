package com.radixdlt.atomos.procedures;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atomos.AtomOS.ParticleClassWithSideEffectConstraintCheck;
import com.radixdlt.atomos.FungibleFormula;
import com.radixdlt.atomos.FungibleTransition;
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
	private final ParticleValueMapper valueMapper;
	private final Map<Class<? extends Particle>, FungibleTransition<? extends Particle>> transitions;

	public FungibleTransitionConstraintProcedure(Map<Class<? extends Particle>, FungibleTransition<? extends Particle>> transitions) {
		Objects.requireNonNull(transitions);

		List<FungibleTransition<? extends Particle>> fungibleTransitions = transitions.entrySet().stream()
			.map(Entry::getValue)
			.collect(Collectors.toList());

		this.transitions = transitions;

		this.valueMapper = ParticleValueMapper.from(fungibleTransitions);
		this.inputTypes = ImmutableSet.copyOf(fungibleTransitions.stream()
			.flatMap(ft -> ft.getAllInputs().stream())
			.collect(Collectors.toSet()));
		this.outputTypes = ImmutableSet.copyOf(fungibleTransitions.stream()
			.map(FungibleTransition::getOutputParticleClass)
			.collect(Collectors.toSet()));

		checkAllTypesKnown(inputTypes, outputTypes);
	}

	private static void checkAllTypesKnown(ImmutableSet<Class<? extends Particle>> inputTypes, ImmutableSet<Class<? extends Particle>> outputTypes) {
		List<Class<? extends Particle>> foreignTypes = inputTypes.stream()
			.filter(inputType -> !outputTypes.contains(inputType))
			.collect(Collectors.toList());

		if (!foreignTypes.isEmpty()) {
			throw new IllegalArgumentException("Foreign input types " + inputTypes + ", must be defined as fungibles");
		}
	}

	@Override
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		final Stack<Pair<Particle, UInt256>> inputs = new Stack<>();
		final Stack<Pair<Particle, UInt256>> outputs = new Stack<>();

		for (int i = 0; i < group.getParticleCount(); i++) {
			SpunParticle sp = group.getSpunParticle(i);
			Particle p = sp.getParticle();
			if (sp.getSpin() == Spin.DOWN && this.inputTypes.contains(p.getClass())) {
				inputs.push(Pair.of(p, valueMapper.amount(p)));
			} else if (sp.getSpin() == Spin.UP && this.outputTypes.contains(p.getClass())) {
				UInt256 currentOutput = valueMapper.amount(p);

				while (!currentOutput.isZero()) {
					if (inputs.empty()) {
						break;
					}
					Pair<Particle, UInt256> top = inputs.peek();
					Particle fromParticle = top.getFirst();
					FungibleFormula formula = transitions.get(fromParticle.getClass()).getParticleClassToFormulaMap().get(p.getClass());
					if (formula == null) {
						break;
					}
					if (!formula.getTransition().test(fromParticle, p)) {
						break;
					}
					if (formula.getWitnessValidator().apply(fromParticle, metadata).isError()) {
						break;
					}

					inputs.pop();
					UInt256 inputAmount = top.getSecond();
					UInt256 min = UInt256.min(inputAmount, currentOutput);
					UInt256 newInputAmount = inputAmount.subtract(min);
					if (!newInputAmount.isZero()) {
						inputs.push(Pair.of(fromParticle, newInputAmount));
					}

					currentOutput = currentOutput.subtract(min);
				}

				if (!currentOutput.isZero()) {
					outputs.push(Pair.of(p, currentOutput));
				}
			}
		}

		if (!inputs.empty()) {
			return Stream.of(ProcedureError.of("Input stack not empty"));
		} else if (!outputs.empty()) {
			final List<Particle> outputParticles = outputs.stream().map(Pair::getFirst).collect(Collectors.toList());
			final Set<Particle> otherOutput = group.particles(Spin.UP).collect(Collectors.toSet());
			for (Particle p : outputParticles) {
				Particle remove = null;
				FungibleTransition<? extends Particle> transition = transitions.get(p.getClass());
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