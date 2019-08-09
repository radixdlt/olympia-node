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
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Stream;

/**
 * Procedure which checks that payload particles can never go into DOWN state
 */
public final class TransitionlessConstraintProcedure implements ConstraintProcedure {
	private final ImmutableMap<Class<? extends Particle>, WitnessValidator<Particle>> transitionlessParticles;
	private final Map<Class<? extends Particle>, InputParticleProcedure> procedures = new HashMap<>();

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
		this.transitionlessParticles.forEach((p, m) -> this.procedures.put(p, (a, b, c) -> false));
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
			} else {
				if (transitionlessParticles.containsKey(p.getClass())) {
					if (transitionlessParticles.get(p.getClass()).validate(p, metadata).isError()) {
						outputs.push(Pair.of(p, null));
					}
				}
			}
		}

		if (!outputs.empty()) {
			return Stream.of(ProcedureError.of("Transitionless Failure Output stack: " + outputs.toString()));
		}

		return Stream.empty();
	}
}
