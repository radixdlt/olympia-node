package com.radixdlt.atomos.procedures;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.AtomOS.WitnessValidator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.ProcedureError;
import java.util.Stack;
import java.util.stream.Stream;

/**
 * Procedure which checks that payload particles can never go into DOWN state
 */
public final class TransitionlessConstraintProcedure implements ConstraintProcedure {
	private final ImmutableMap<Class<? extends Particle>, WitnessValidator<Particle>> transitionlessParticles;

	public static final class Builder {
		private final ImmutableMap.Builder<Class<? extends Particle>, WitnessValidator<Particle>> setBuilder = new ImmutableMap.Builder<>();

		public <T extends Particle> Builder add(Class<T> particleClass, WitnessValidator<T> witnessValidator) {
			setBuilder.put(particleClass, (e, m) -> witnessValidator.validate((T) e, m));
			return this;
		}

		public TransitionlessConstraintProcedure build() {
			return new TransitionlessConstraintProcedure(setBuilder.build());
		}
	}

	TransitionlessConstraintProcedure(ImmutableMap<Class<? extends Particle>, WitnessValidator<Particle>> transitionlessParticles) {
		this.transitionlessParticles = transitionlessParticles;
	}

	private boolean transitionlessInputParticleExecutor(Particle input, AtomMetadata metadata, Stack<Pair<Particle, Void>> outputs) {
		return false;
	}

	@Override
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		final Stack<Pair<Particle, Void>> inputs = new Stack<>();
		final Stack<Pair<Particle, Void>> outputs = new Stack<>();

		for (int i = group.getParticleCount() - 1; i >= 0; i--) {
			SpunParticle sp = group.getSpunParticle(i);
			Particle p = sp.getParticle();
			if (sp.getSpin() == Spin.DOWN) {
				if (transitionlessParticles.containsKey(p.getClass())) {
					if (!this.transitionlessInputParticleExecutor(p, metadata, outputs)) {
						inputs.push(Pair.of(p, null));
					}
				}
			} else {
				if (transitionlessParticles.containsKey(p.getClass())) {
					if (transitionlessParticles.get(p.getClass()).validate(p, metadata).isError()) {
						outputs.push(Pair.of(p, null));
					}
				}
			}
		}

		if (!inputs.empty() || !outputs.empty()) {
			return Stream.of(ProcedureError.of("Transitionless Failure Input stack: " + inputs.toString() + " Output stack: " + outputs.toString()));
		}

		return Stream.empty();
	}
}
