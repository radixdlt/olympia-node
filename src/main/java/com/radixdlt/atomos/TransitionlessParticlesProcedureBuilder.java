package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.AtomOS.WitnessValidator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ParticleProcedure;
import java.util.Stack;

/**
 * Procedure which checks that payload particles can never go into DOWN state
 */
public final class TransitionlessParticlesProcedureBuilder {
	private final ImmutableMap.Builder<Class<? extends Particle>, ParticleProcedure> procedureBuilder = new ImmutableMap.Builder<>();

	public <T extends Particle> TransitionlessParticlesProcedureBuilder add(Class<T> particleClass, WitnessValidator<T> witnessValidator) {
		procedureBuilder.put(particleClass, new ParticleProcedure() {
			@Override
			public boolean inputExecute(Particle input, AtomMetadata metadata, Stack<Pair<Particle, Object>> outputs) {
																													return false;
																																 }

			@Override
			public boolean outputExecute(Particle output, AtomMetadata metadata) {
				return witnessValidator.validate((T) output, metadata).isSuccess();
			}
		});
		return this;
	}

	public ImmutableMap<Class<? extends Particle>, ParticleProcedure> build() {
		return procedureBuilder.build();
	}
}
