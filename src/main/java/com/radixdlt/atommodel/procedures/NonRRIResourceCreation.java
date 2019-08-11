package com.radixdlt.atommodel.procedures;

import com.radixdlt.atomos.WitnessValidator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Procedure which checks that payload particles
 */
public final class NonRRIResourceCreation<T extends Particle> implements TransitionProcedure<Particle, T> {
	private final WitnessValidator<T> witnessValidator;

	public NonRRIResourceCreation(WitnessValidator<T> witnessValidator) {
		this.witnessValidator = witnessValidator;
	}

	@Override
	public ProcedureResult execute(
		Particle inputParticle,
		AtomicReference<Object> inputData,
		T outputParticle,
		AtomicReference<Object> outputData
	) {
		return ProcedureResult.POP_OUTPUT;
	}

	@Override
	public boolean validateWitness(
		ProcedureResult result,
		Particle inputParticle,
		T outputParticle,
		AtomMetadata metadata
	) {
		return witnessValidator.validate(outputParticle, metadata).isSuccess();
	}

}
