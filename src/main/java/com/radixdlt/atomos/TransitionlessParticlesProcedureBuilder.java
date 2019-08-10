package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.SysCalls.WitnessValidator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ParticleProcedure;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Procedure which checks that payload particles can never go into DOWN state
 */
public final class TransitionlessParticlesProcedureBuilder {
	private final ImmutableMap.Builder<Class<? extends Particle>, ParticleProcedure> procedureBuilder = new ImmutableMap.Builder<>();

	public <T extends Particle> TransitionlessParticlesProcedureBuilder add(Class<T> particleClass, WitnessValidator<T> witnessValidator) {
		procedureBuilder.put(particleClass, new ParticleProcedure() {
			@Override
			public boolean validateWitness(
				ProcedureResult result,
				Particle inputParticle,
				Particle outputParticle,
				AtomMetadata metadata
			) {
				return witnessValidator.validate((T) outputParticle, metadata).isSuccess();
			}

			@Override
			public ProcedureResult execute(
				Particle inputParticle,
				AtomicReference<Object> inputData,
				Particle outputParticle,
				AtomicReference<Object> outputData
			) {
				return ProcedureResult.POP_OUTPUT;
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
