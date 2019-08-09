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
	private final Map<Class<? extends Particle>, ParticleProcedure> procedures = new HashMap<>();

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
		this.transitionlessParticles.forEach((p, m) -> this.procedures.put(p, new ParticleProcedure() {
			@Override
			public boolean inputExecute(Particle input, AtomMetadata metadata, Stack<Pair<Particle, Object>> outputs) {
				return false;
			}

			@Override
			public boolean outputExecute(Particle output, AtomMetadata metadata) {
				return transitionlessParticles.get(output.getClass()).validate(output, metadata).isSuccess();
			}
		}));
	}

	@Override
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		final Stack<Pair<Particle, Object>> outputs = new Stack<>();

		for (int i = group.getParticleCount() - 1; i >= 0; i--) {
			SpunParticle sp = group.getSpunParticle(i);
			Particle p = sp.getParticle();
			ParticleProcedure particleProcedure = this.procedures.get(p.getClass());
			if (particleProcedure == null) {
				continue;
			}
			if (sp.getSpin() == Spin.DOWN) {
				if (!particleProcedure.inputExecute(p, metadata, outputs)) {
					return Stream.of(ProcedureError.of("Transitionless Failure Input " + p + " failed. Output stack: " + outputs));
				}
			} else {
				if (!particleProcedure.outputExecute(p, metadata)) {
					outputs.push(Pair.of(p, null));
				}
			}
		}

		if (!outputs.empty()) {
			return Stream.of(ProcedureError.of("Transitionless Failure Output stack: " + outputs.toString()));
		}

		return Stream.empty();
	}
}
