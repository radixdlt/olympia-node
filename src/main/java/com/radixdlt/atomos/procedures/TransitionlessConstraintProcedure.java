package com.radixdlt.atomos.procedures;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.AtomOS.WitnessValidator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.ProcedureError;
import java.util.stream.Stream;

/**
 * Procedure which checks that payload particles can never go into DOWN state
 */
public final class TransitionlessConstraintProcedure implements ConstraintProcedure {
	private final ImmutableMap<Class<? extends Particle>, WitnessValidator<Particle>> transitionlessParticles;

	public static final class Builder {
		private final ImmutableMap.Builder<Class<? extends Particle>, WitnessValidator<Particle>> setBuilder = new ImmutableMap.Builder<>();

		public <T extends Particle> Builder add(Class<T> particleClass, WitnessValidator<T> witnessValidator) {
			setBuilder.put(particleClass, (e, m) -> witnessValidator.apply((T) e, m));
			return this;
		}

		public TransitionlessConstraintProcedure build() {
			return new TransitionlessConstraintProcedure(setBuilder.build());
		}
	}

	TransitionlessConstraintProcedure(ImmutableMap<Class<? extends Particle>, WitnessValidator<Particle>> transitionlessParticles) {
		this.transitionlessParticles = transitionlessParticles;
	}

	@Override
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		return group.spunParticlesWithIndex((s, i) -> {
			final Class<? extends Particle> particleClass = s.getParticle().getClass();
			final boolean isPayload = transitionlessParticles.containsKey(particleClass);

			if (isPayload) {
				if (s.getSpin() == Spin.DOWN) {
					return Stream.of(ProcedureError.of(group, "Payload Particle " + particleClass + " cannot be DOWN", i));
				} else {
					if (transitionlessParticles.get(particleClass).apply(s.getParticle(), metadata).isError()) {
						return Stream.of(ProcedureError.of("witness validation failed"));
					}
				}
			}

			return Stream.<ProcedureError>empty();
		}).flatMap(l -> l);
	}
}
