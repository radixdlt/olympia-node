package com.radixdlt.atomos.procedures;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.AtomOS.WitnessValidator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

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
	public Map<Class<? extends Particle>, ParticleProcedure> getProcedures() {
		return procedures;
	}
}
