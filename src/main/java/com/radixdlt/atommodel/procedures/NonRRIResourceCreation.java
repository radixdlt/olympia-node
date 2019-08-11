package com.radixdlt.atommodel.procedures;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Procedure which checks that payload particles
 */
public final class NonRRIResourceCreation<T extends Particle> implements TransitionProcedure {
	private final Class<T> particleClass;
	private final WitnessValidator witnessValidator;

	public NonRRIResourceCreation(Class<T> particleClass, WitnessValidator<T> witnessValidator) {
		this.particleClass = particleClass;
		this.witnessValidator = witnessValidator;
	}

	@Override
	public Pair<Class<? extends Particle>, Class<? extends Particle>> supports() {
		return Pair.of(null, particleClass);
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
	public boolean validateWitness(
		ProcedureResult result,
		Particle inputParticle,
		Particle outputParticle,
		AtomMetadata metadata
	) {
		return witnessValidator.validate((T) outputParticle, metadata).isSuccess();
	}

}
