package com.radixdlt.atomos.procedures;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.stream.Stream;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.ProcedureError;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;

/**
 * Procedure which checks that payload particles can never go into DOWN state
 */
public final class PayloadParticleConstraintProcedure implements ConstraintProcedure {
	private final Set<Class<? extends Particle>> payloadParticles;

	public static final class Builder {
		private final ImmutableSet.Builder<Class<? extends Particle>> setBuilder = new ImmutableSet.Builder<>();

		public Builder add(Class<? extends Particle> particleClass) {
			setBuilder.add(particleClass);
			return this;
		}

		public PayloadParticleConstraintProcedure build() {
			return new PayloadParticleConstraintProcedure(setBuilder.build());
		}
	}

	PayloadParticleConstraintProcedure(ImmutableSet<Class<? extends Particle>> payloadParticles) {
		this.payloadParticles = payloadParticles;
	}

	@Override
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		return group.spunParticlesWithIndex((s, i) -> {
			final Class<? extends Particle> particleClass = s.getParticle().getClass();
			final boolean isPayload = payloadParticles.contains(particleClass);

			if (isPayload && s.getSpin() == Spin.DOWN) {
				return Stream.of(ProcedureError.of(group, "Payload Particle " + particleClass + " cannot be DOWN", i));
			}

			return Stream.<ProcedureError>empty();
		}).flatMap(l -> l);
	}
}
